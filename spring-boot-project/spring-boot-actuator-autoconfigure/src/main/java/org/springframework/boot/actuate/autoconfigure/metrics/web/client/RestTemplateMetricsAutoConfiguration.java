/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.autoconfigure.metrics.web.client;

import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.metrics.web.client.DefaultRestTemplateExchangeTagsProvider;
import org.springframework.boot.actuate.metrics.web.client.MetricsRestTemplateCustomizer;
import org.springframework.boot.actuate.metrics.web.client.RestTemplateExchangeTagsProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.RestTemplate;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link RestTemplate}-related
 * metrics.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 * @since 2.0.0
 */
@Configuration
@AutoConfigureAfter({MetricsAutoConfiguration.class, RestTemplateAutoConfiguration.class,
		SimpleMetricsExportAutoConfiguration.class})
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnBean(MeterRegistry.class)
public class RestTemplateMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(RestTemplateExchangeTagsProvider.class)
	public DefaultRestTemplateExchangeTagsProvider restTemplateTagConfigurer() {
		return new DefaultRestTemplateExchangeTagsProvider();
	}

	@Bean
	public MetricsRestTemplateCustomizer metricsRestTemplateCustomizer(
			MeterRegistry meterRegistry,
			RestTemplateExchangeTagsProvider restTemplateTagConfigurer,
			MetricsProperties properties) {
		return new MetricsRestTemplateCustomizer(meterRegistry, restTemplateTagConfigurer,
				properties.getWeb().getClient().getRequestsMetricName());
	}

	@Bean
	@Order(0)
	public MeterFilter metricsWebClientUriTagFilter(MetricsProperties properties) {
		String metricName = properties.getWeb().getClient().getRequestsMetricName();
		MeterFilter denyFilter = new MaximumUriTagsReachedMeterFilter(metricName);
		return MeterFilter.maximumAllowableTags(metricName, "uri",
				properties.getWeb().getClient().getMaxUriTags(), denyFilter);
	}

	/**
	 * {@link MeterFilter} to deny further URI tags and log a warning.
	 */
	private static class MaximumUriTagsReachedMeterFilter implements MeterFilter {

		private final Logger logger = LoggerFactory
				.getLogger(MaximumUriTagsReachedMeterFilter.class);

		private final String metricName;

		private final AtomicBoolean alreadyWarned = new AtomicBoolean(false);

		MaximumUriTagsReachedMeterFilter(String metricName) {
			this.metricName = metricName;
		}

		@Override
		public MeterFilterReply accept(Id id) {
			if (this.alreadyWarned.compareAndSet(false, true)) {
				logWarning();
			}
			return MeterFilterReply.DENY;
		}

		private void logWarning() {
			if (this.logger.isWarnEnabled()) {
				this.logger.warn(
						"Reached the maximum number of URI tags for '" + this.metricName
								+ "'. Are you using uriVariables on RestTemplate calls?");
			}
		}

	}

}
