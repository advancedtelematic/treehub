package com.advancedtelematic.treehub

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Metric, MetricFilter, Slf4jReporter}
import org.genivi.sota.monitoring.MetricsSupport



trait AppMetricsSupport {
  val TreeHubFilter = new MetricFilter {
    override def matches(name: String, metric: Metric): Boolean = name.startsWith("app")
  }

  private lazy val reporter = Slf4jReporter.forRegistry(MetricsSupport.metricRegistry)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .filter(TreeHubFilter)
    .build()

  reporter.start(10, TimeUnit.SECONDS)
}
