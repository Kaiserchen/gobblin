/* global Backbone, jQuery, Gobblin */
var app = app || {}

;(function ($) {
  app.JobExecution = Backbone.Model.extend({
    idAttribute: 'jobId',

    constructor: function (attributes, options) {
      this.taskExecutions = []
      if (attributes.taskExecutions) {
        for (var i in attributes.taskExecutions) {
          this.taskExecutions.push(new app.TaskExecution(attributes.taskExecutions[i]))
        }
      }
      Backbone.Model.apply(this, arguments)
    },

    hasMetrics: function () {
      return this.attributes.metrics && this.attributes.metrics.length > 0
    },
    hasProperties: function () {
      return this.attributes.jobProperties && !$.isEmptyObject(this.attributes.jobProperties)
    },

    getTaskExecutions: function () {
      return this.taskExecutions
    },
    getDescription: function () {
      if (this.hasProperties()) {
        return this.attributes.jobProperties['job.description']
      }
      return ''
    },
    getJobNameLink: function () {
      return "<a href='#job/" + this.attributes.jobName + "'>" + this.attributes.jobName + '</a>'
    },
    getJobIdLink: function () {
      return "<a href='#job-details/" + this.attributes.jobId + "'>" + this.attributes.jobId + '</a>'
    },
    getJobStateMapped: function () {
      return Gobblin.stateMap[this.attributes.state].class
    },
    getJobStateElem: function () {
      return app.JobExecution.getJobStateElemByState(this.attributes.state)
    },
    getJobState: function () {
      return this.attributes.state
    },
    getLauncherType: function () {
      return this.attributes.launcherType
    },
    getDurationInSeconds: function () {
      if (this.attributes.state === 'COMMITTED') {
        return (this.attributes.endTime - this.attributes.startTime) / 1000
      }
      return '-'
    },
    getJobStartTime: function () {
      return this._formatTime(this.attributes.startTime)
    },
    getJobEndTime: function () {
      return this._formatTime(this.attributes.endTime)
    },
    getSchedule: function () {
      if (this.hasProperties()) {
        if ('job.runonce' in this.attributes.jobProperties) {
          return 'RUN_ONCE'
        } else if ('job.schedule' in this.attributes.jobProperties) {
          return this.attributes.jobProperties['job.schedule']
        }
      }
      return '-'
    },
    getTaskRatio: function () {
      return this.attributes.completedTasks + '/' + this.attributes.launchedTasks
    },
    getRecordMetrics: function () {
      if (this.hasMetrics()) {
        var recordsRead = this.getRecordsRead()
        var recordsFailed = this.getRecordsFailed()
        var s = recordsRead + " <span class='text-"
        s += (recordsFailed !== 0) ? 'danger-bold' : 'muted'
        s += "'>(" + recordsFailed + ' failures)</span>'
        return s
      }
      return '-'
    },
    getRecordsRead: function () {
      if (this.hasMetrics()) {
        var recordsRead = $.grep(
          this.attributes.metrics, function (e) {
            return e.name.match(/JOB.*\.records$/)}
        )
        if (recordsRead.length === 1) {
          var val = parseFloat(recordsRead[0].value)
          if (!isNaN(val)) {
            return val
          } else {
            return recordsRead[0].value
          }
        }
      }
      return 0
    },
    getRecordsFailed: function () {
      if (this.hasMetrics()) {
        var recordsFailed = $.grep(this.attributes.metrics, function (e) { return e.name === 'gobblin.extractor.records.failed' })
        if (recordsFailed.length === 1) {
          var val = parseFloat(recordsFailed[0].value)
          if (!isNaN(val)) {
            return val
          } else {
            return recordsFailed[0].value
          }
        }
      }
      return 0
    },
    _formatTime: function (timeAsLong) {
      var timeAsDate = new Date(timeAsLong)
      return timeAsDate.toLocaleString()
    }
  }, {
    // Static methods
    getJobStateElemByState: function (state) {
      return "<span class='highlight text-highlight highlight-" + Gobblin.stateMap[state].class + "'>" + state + '</span>'
    }
  })
})(jQuery)
