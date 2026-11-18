package cz.healthwidgy.demo;

import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration;
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Device;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.request.AggregateGroupByDurationRequest;
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.response.ReadRecordsResponse;
import androidx.health.connect.client.time.TimeRangeFilter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parts of the Health Connect client library that a fake client needs but Kotlin callers cannot reach:
 * request fields and result constructors are Kotlin-internal. Java sees them as ordinary public members.
 * Demo build only; tied to connect-client 1.1.0 (check the names with javap after an upgrade).
 */
final class HcInternals {
  private HcInternals() {}

  /** Metadata as Health Connect returns it for a record written by another app. */
  static Metadata metadata(String id, DataOrigin origin, Instant lastModified, Device device) {
    return new Metadata(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED, id, origin, lastModified, null, 0L, device);
  }

  static <T extends Record> ReadRecordsResponse<T> readResponse(List<T> records, String pageToken) {
    return new ReadRecordsResponse<>(records, pageToken);
  }

  /** Raw values: counts, metres, kilocalories and beats per minute (the units the metrics' converters expect). */
  static AggregationResult result(Map<String, Long> longs, Map<String, Double> doubles, Set<DataOrigin> origins) {
    return new AggregationResult(longs, doubles, origins);
  }

  static AggregationResultGroupedByPeriod byPeriod(AggregationResult result, LocalDateTime start, LocalDateTime end) {
    return new AggregationResultGroupedByPeriod(result, start, end, false);
  }

  static AggregationResultGroupedByDuration byDuration(AggregationResult result, Instant start, Instant end, ZoneOffset offset) {
    return new AggregationResultGroupedByDuration(result, start, end, offset, false);
  }

  static Set<AggregateMetric<?>> metrics(AggregateRequest r) {
    return r.getMetrics$connect_client_release();
  }

  static TimeRangeFilter range(AggregateRequest r) {
    return r.getTimeRangeFilter$connect_client_release();
  }

  static Set<DataOrigin> origins(AggregateRequest r) {
    return r.getDataOriginFilter$connect_client_release();
  }

  static Set<AggregateMetric<?>> metrics(AggregateGroupByPeriodRequest r) {
    return r.getMetrics$connect_client_release();
  }

  static TimeRangeFilter range(AggregateGroupByPeriodRequest r) {
    return r.getTimeRangeFilter$connect_client_release();
  }

  static Period slicer(AggregateGroupByPeriodRequest r) {
    return r.getTimeRangeSlicer$connect_client_release();
  }

  static Set<DataOrigin> origins(AggregateGroupByPeriodRequest r) {
    return r.getDataOriginFilter$connect_client_release();
  }

  static Set<AggregateMetric<?>> metrics(AggregateGroupByDurationRequest r) {
    return r.getMetrics$connect_client_release();
  }

  static TimeRangeFilter range(AggregateGroupByDurationRequest r) {
    return r.getTimeRangeFilter$connect_client_release();
  }

  static Duration slicer(AggregateGroupByDurationRequest r) {
    return r.getTimeRangeSlicer$connect_client_release();
  }

  static Set<DataOrigin> origins(AggregateGroupByDurationRequest r) {
    return r.getDataOriginFilter$connect_client_release();
  }
}
