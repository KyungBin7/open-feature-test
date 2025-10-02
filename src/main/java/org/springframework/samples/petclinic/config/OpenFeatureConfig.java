/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.config;

import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenFeature 설정 클래스 flagd provider를 사용하여 feature flag를 평가합니다.
 */
@Configuration
public class OpenFeatureConfig {

	private static final Logger log = LoggerFactory.getLogger(OpenFeatureConfig.class);

	/**
	 * flagd 호스트 (기본값: localhost, OpenShift에서는 Sidecar 패턴)
	 */
	@Value("${openfeature.flagd.host:localhost}")
	private String flagdHost;

	/**
	 * flagd gRPC 포트 (기본값: 8013)
	 */
	@Value("${openfeature.flagd.port:8013}")
	private int flagdPort;

	/**
	 * OpenFeatureAPI Bean 생성
	 * @return OpenFeatureAPI 싱글톤 인스턴스
	 */
	@Bean
	public OpenFeatureAPI openFeatureAPI() {
		final OpenFeatureAPI api = OpenFeatureAPI.getInstance();

		try {
			// flagd 프로바이더 옵션 설정
			FlagdOptions options = FlagdOptions.builder().host(flagdHost).port(flagdPort).build();

			// flagd 프로바이더 생성 및 설정
			FlagdProvider provider = new FlagdProvider(options);
			api.setProviderAndWait(provider);

			log.info("✅ OpenFeature initialized successfully");
			log.info("   Provider: flagd");
			log.info("   Host: {}", flagdHost);
			log.info("   Port: {}", flagdPort);

		}
		catch (OpenFeatureError e) {
			log.error("❌ Failed to initialize OpenFeature provider", e);
			throw new RuntimeException("OpenFeature initialization failed", e);
		}

		return api;
	}

}
