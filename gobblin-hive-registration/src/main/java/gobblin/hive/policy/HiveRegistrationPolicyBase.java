/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.hive.policy;

import java.io.IOException;
import java.util.Collection;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.TableType;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import gobblin.annotation.Alpha;
import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.hive.HivePartition;
import gobblin.hive.HiveRegProps;
import gobblin.hive.HiveSerDeManager;
import gobblin.hive.HiveTable;
import gobblin.hive.spec.HiveSpec;
import gobblin.hive.spec.SimpleHiveSpec;


/**
 * A base implementation of {@link HiveRegistrationPolicy}. It obtains database name from
 * property {@link #HIVE_DATABASE_NAME} or {@link #HIVE_DATABASE_REGEX} (group 1), obtains
 * table name from property {@link #HIVE_TABLE_NAME} and {@link #HIVE_TABLE_REGEX} (group 1),
 * and builds a {@link SimpleHiveSpec}.
 *
 * @author ziliu
 */
@Alpha
public class HiveRegistrationPolicyBase implements HiveRegistrationPolicy {

  public static final String HIVE_FS_URI = "hive.registration.fs.uri";
  public static final String HIVE_DATABASE_NAME = "hive.database.name";
  public static final String HIVE_DATABASE_REGEX = "hive.database.regex";
  public static final String HIVE_DATABASE_NAME_PREFIX = "hive.database.name.prefix";
  public static final String HIVE_DATABASE_NAME_SUFFIX = "hive.database.name.suffix";
  public static final String HIVE_TABLE_NAME = "hive.table.name";
  public static final String HIVE_TABLE_REGEX = "hive.table.regex";
  public static final String HIVE_TABLE_NAME_PREFIX = "hive.table.name.prefix";
  public static final String HIVE_TABLE_NAME_SUFFIX = "hive.table.name.suffix";
  public static final String HIVE_SANITIZE_INVALID_NAMES = "hive.sanitize.invalid.names";

  /**
   * A valid db or table name should start with an alphanumeric character, and contains only
   * alphanumeric characters and '_'.
   */
  private static final Pattern VALID_DB_TABLE_NAME_PATTERN_1 = Pattern.compile("[a-z0-9][a-z0-9_]*");

  /**
   * A valid db or table name should contain at least one letter or '_' (i.e., should not be numbers only).
   */
  private static final Pattern VALID_DB_TABLE_NAME_PATTERN_2 = Pattern.compile(".*[a-z_].*");

  protected final HiveRegProps props;
  protected final boolean sanitizeNameAllowed;
  protected final Optional<Pattern> dbNamePattern;
  protected final Optional<Pattern> tableNamePattern;
  protected final String dbNamePrefix;
  protected final String dbNameSuffix;
  protected final String tableNamePrefix;
  protected final String tableNameSuffix;

  protected HiveRegistrationPolicyBase(State props) {
    Preconditions.checkNotNull(props);
    this.props = new HiveRegProps(props);
    this.sanitizeNameAllowed = props.getPropAsBoolean(HIVE_SANITIZE_INVALID_NAMES, true);
    this.dbNamePattern = props.contains(HIVE_DATABASE_REGEX)
        ? Optional.of(Pattern.compile(props.getProp(HIVE_DATABASE_REGEX))) : Optional.<Pattern> absent();
    this.tableNamePattern = props.contains(HIVE_TABLE_REGEX)
        ? Optional.of(Pattern.compile(props.getProp(HIVE_TABLE_REGEX))) : Optional.<Pattern> absent();
    this.dbNamePrefix = props.getProp(HIVE_DATABASE_NAME_PREFIX, StringUtils.EMPTY);
    this.dbNameSuffix = props.getProp(HIVE_DATABASE_NAME_SUFFIX, StringUtils.EMPTY);
    this.tableNamePrefix = props.getProp(HIVE_TABLE_NAME_PREFIX, StringUtils.EMPTY);
    this.tableNameSuffix = props.getProp(HIVE_TABLE_NAME_SUFFIX, StringUtils.EMPTY);
  }

  /**
   * This method first tries to obtain the database name from {@link #HIVE_DATABASE_NAME}.
   * If this property is not specified, it then tries to obtain the database name using
   * the first group of {@link #HIVE_DATABASE_REGEX}.
   */
  protected String getDatabaseName(Path path) {
    return this.dbNamePrefix + getDatabaseOrTableName(path, HIVE_DATABASE_NAME, HIVE_DATABASE_REGEX, this.dbNamePattern)
        + this.dbNameSuffix;
  }

  /**
   * This method first tries to obtain the database name from {@link #HIVE_TABLE_NAME}.
   * If this property is not specified, it then tries to obtain the database name using
   * the first group of {@link #HIVE_TABLE_REGEX}.
   */
  protected String getTableName(Path path) {
    return this.tableNamePrefix + getDatabaseOrTableName(path, HIVE_TABLE_NAME, HIVE_TABLE_REGEX, this.tableNamePattern)
        + this.tableNameSuffix;
  }

  protected String getDatabaseOrTableName(Path path, String nameKey, String regexKey, Optional<Pattern> pattern) {
    String name;
    if (this.props.contains(nameKey)) {
      name = this.props.getProp(nameKey);
    } else if (pattern.isPresent()) {
      name = pattern.get().matcher(path.toString()).group();
    } else {
      throw new IllegalStateException("Missing required property " + nameKey + " or " + regexKey);
    }

    name = name.toLowerCase();

    if (this.sanitizeNameAllowed && !isNameValid(name)) {
      name = sanitizeName(name);
    }

    if (isNameValid(name)) {
      return name;
    }

    throw new IllegalStateException(name + " is not a valid Hive database or table name");
  }

  /**
   * A base implementation for creating a non bucketed, external {@link HiveTable} given a {@link Path}.
   *
   * @param path a {@link Path} used to create the {@link HiveTable}.
   * @return a {@link HiveTable} for the given {@link Path}.
   * @throws IOException
   */
  protected HiveTable getTable(Path path) throws IOException {
    HiveTable table = new HiveTable.Builder().withDbName(getDatabaseName(path)).withTableName(getTableName(path))
        .withSerdeManaager(HiveSerDeManager.get(this.props)).build();

    table.setLocation(getTableLocation(path));
    table.setSerDeProps();
    table.setProps(this.props.getTablePartitionProps());
    table.setStorageProps(this.props.getStorageProps());
    table.setSerDeProps(this.props.getSerdeProps());
    table.setNumBuckets(-1);
    table.setTableType(TableType.EXTERNAL_TABLE.toString());
    return table;
  }

  protected Optional<HivePartition> getPartition(Path path) throws IOException {
    return Optional.<HivePartition> absent();
  }

  protected String getTableLocation(Path path) {
    return path.toString();
  }

  /**
   * Determine whether a database or table name is valid.
   *
   * A name is valid if and only if: it starts with an alphanumeric character, contains only alphanumeric characters
   * and '_', and is NOT composed of numbers only.
   */
  protected static boolean isNameValid(String name) {
    Preconditions.checkNotNull(name);
    name = name.toLowerCase();
    return VALID_DB_TABLE_NAME_PATTERN_1.matcher(name).matches()
        && VALID_DB_TABLE_NAME_PATTERN_2.matcher(name).matches();
  }

  /**
   * Attempt to sanitize an invalid database or table name by replacing characters that are not alphanumeric
   * or '_' with '_'.
   */
  protected static String sanitizeName(String name) {
    return name.replaceAll("[^a-zA-Z0-9_]", "_");
  }

  @Override
  public Collection<HiveSpec> getHiveSpecs(Path path) throws IOException {
    return ImmutableList.<HiveSpec> of(
        new SimpleHiveSpec.Builder<>(path).withTable(getTable(path)).withPartition(getPartition(path)).build());
  }

  /**
   * Get a {@link HiveRegistrationPolicy} from a {@link State} object.
   *
   * @param props A {@link State} object that contains property, {@link #HIVE_REGISTRATION_POLICY},
   * which is the class name of the desired policy. This policy class must have a constructor that
   * takes a {@link State} object.
   */
  public static HiveRegistrationPolicy getPolicy(State props) {
    Preconditions.checkArgument(props.contains(ConfigurationKeys.HIVE_REGISTRATION_POLICY));

    String policyType = props.getProp(ConfigurationKeys.HIVE_REGISTRATION_POLICY);
    try {
      return (HiveRegistrationPolicy) ConstructorUtils.invokeConstructor(Class.forName(policyType), props);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Unable to instantiate " + HiveRegistrationPolicy.class.getSimpleName() + " with type " + policyType, e);
    }
  }
}
