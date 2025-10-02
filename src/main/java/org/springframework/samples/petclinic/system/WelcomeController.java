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

package org.springframework.samples.petclinic.system;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;

/**
 * Welcome 페이지 컨트롤러 OpenFeature를 사용하여 A/B 테스트를 수행합니다.
 */
@Controller
class WelcomeController {

	private static final Logger log = LoggerFactory.getLogger(WelcomeController.class);

	// Feature Flag 키
	private static final String FLAG_KEY = "welcome-page-redesign";

	// 사용자 식별 쿠키 이름
	private static final String USER_ID_COOKIE = "petclinic_user_id";

	// 쿠키 유효기간: 5분 (초 단위)
	private static final int COOKIE_MAX_AGE = 5 * 60;

	private final OpenFeatureAPI openFeatureAPI;

	/**
	 * 생성자 주입
	 */
	public WelcomeController(OpenFeatureAPI openFeatureAPI) {
		this.openFeatureAPI = openFeatureAPI;
	}

	/**
	 * 메인 페이지 핸들러 Feature Flag를 평가하여 A 또는 B 버전을 보여줍니다.
	 */
	@GetMapping("/")
	public String welcome(HttpServletRequest request, HttpServletResponse response, Model model) {

		// 1. 사용자 식별자 가져오기 또는 생성
		String userId = getUserId(request, response);

		// 2. Evaluation Context 생성
		EvaluationContext context = buildEvaluationContext(userId, request);

		// 3. Feature Flag 평가
		Client client = openFeatureAPI.getClient();
		boolean useNewDesign = client.getBooleanValue(FLAG_KEY, false, // 기본값: false (A
																		// 버전)
				context);

		// 4. 로깅
		log.debug("User {} → {} (flag={})", userId, useNewDesign ? "Variant B (new)" : "Variant A (old)", useNewDesign);

		// 5. 모델에 속성 추가
		model.addAttribute("useNewDesign", useNewDesign);
		model.addAttribute("version", useNewDesign ? "v2" : "v1");
		model.addAttribute("userId", userId);

		return "welcome";
	}

	/**
	 * 쿠키에서 사용자 ID 가져오기 또는 생성
	 */
	private String getUserId(HttpServletRequest request, HttpServletResponse response) {
		// 기존 쿠키 확인
		if (request.getCookies() != null) {
			return Arrays.stream(request.getCookies())
				.filter(c -> USER_ID_COOKIE.equals(c.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElseGet(() -> createUserId(response));
		}
		return createUserId(response);
	}

	/**
	 * 새 사용자 ID 생성 및 쿠키 저장
	 */
	private String createUserId(HttpServletResponse response) {
		String userId = UUID.randomUUID().toString();
		Cookie cookie = new Cookie(USER_ID_COOKIE, userId);
		cookie.setMaxAge(COOKIE_MAX_AGE); // 5분
		cookie.setPath("/");
		cookie.setHttpOnly(true);
		response.addCookie(cookie);
		log.debug("Created new user ID: {}", userId);
		return userId;
	}

	/**
	 * Evaluation Context 생성 Feature Flag 평가 시 사용할 컨텍스트 정보를 수집합니다.
	 */
	private EvaluationContext buildEvaluationContext(String userId, HttpServletRequest request) {
		return new MutableContext(userId).add("userAgent", request.getHeader("User-Agent"))
			.add("ipAddress", getClientIp(request))
			.add("referer", request.getHeader("Referer"));
	}

	/**
	 * 클라이언트 IP 주소 가져오기 X-Forwarded-For 헤더를 우선적으로 확인합니다.
	 */
	private String getClientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("X-Real-IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}

}
