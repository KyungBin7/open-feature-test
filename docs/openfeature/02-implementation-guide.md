# OpenFeature 구현 가이드

> **작성일**: 2025-10-01
> **대상**: Spring Boot 개발자
> **난이도**: 초급~중급

## 📑 목차

- [1. 의존성 추가](#1-의존성-추가)
- [2. OpenFeature 설정](#2-openfeature-설정)
- [3. Controller 수정](#3-controller-수정)
- [4. 템플릿 수정](#4-템플릿-수정)
- [5. 테스트 작성](#5-테스트-작성)
- [6. 로컬 테스트](#6-로컬-테스트)

---

## 1. 의존성 추가

### 1.1 pom.xml 수정

`pom.xml`에 OpenFeature 관련 의존성을 추가합니다.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">

  <!-- 기존 내용... -->

  <properties>
    <!-- 기존 properties... -->

    <!-- OpenFeature 버전 -->
    <openfeature.version>1.15.1</openfeature.version>
    <flagd-provider.version>0.11.10</flagd-provider.version>
  </properties>

  <dependencies>
    <!-- 기존 dependencies... -->

    <!-- OpenFeature SDK -->
    <dependency>
      <groupId>dev.openfeature</groupId>
      <artifactId>sdk</artifactId>
      <version>${openfeature.version}</version>
    </dependency>

    <!-- flagd Provider -->
    <dependency>
      <groupId>dev.openfeature.contrib.providers</groupId>
      <artifactId>flagd</artifactId>
      <version>${flagd-provider.version}</version>
    </dependency>
  </dependencies>

</project>
```

### 1.2 의존성 확인

```bash
# Maven 의존성 다운로드
./mvnw clean install -DskipTests

# 의존성 트리 확인
./mvnw dependency:tree | grep openfeature
```

**예상 출력**:
```
[INFO] +- dev.openfeature:sdk:jar:1.15.1:compile
[INFO] +- dev.openfeature.contrib.providers:flagd:jar:0.11.10:compile
```

---

## 2. OpenFeature 설정

### 2.1 Configuration 클래스 생성

`src/main/java/org/springframework/samples/petclinic/config/OpenFeatureConfig.java` 파일을 생성합니다.

```java
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
 * OpenFeature 설정 클래스
 * flagd provider를 사용하여 feature flag를 평가합니다.
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
	 *
	 * @return OpenFeatureAPI 싱글톤 인스턴스
	 */
	@Bean
	public OpenFeatureAPI openFeatureAPI() {
		final OpenFeatureAPI api = OpenFeatureAPI.getInstance();

		try {
			// flagd 프로바이더 옵션 설정
			FlagdOptions options = FlagdOptions.builder()
				.host(flagdHost)
				.port(flagdPort)
				.build();

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
```

### 2.2 application.properties 설정

`src/main/resources/application.properties`에 OpenFeature 설정을 추가합니다.

```properties
# OpenFeature Configuration
openfeature.flagd.host=localhost
openfeature.flagd.port=8013

# Logging (선택 사항)
logging.level.dev.openfeature=DEBUG
logging.level.org.springframework.samples.petclinic.system.WelcomeController=DEBUG
```

---

## 3. Controller 수정

### 3.1 WelcomeController 수정

`src/main/java/org/springframework/samples/petclinic/system/WelcomeController.java` 파일을 수정합니다.

```java
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
 * Welcome 페이지 컨트롤러
 * OpenFeature를 사용하여 A/B 테스트를 수행합니다.
 */
@Controller
class WelcomeController {

	private static final Logger log = LoggerFactory.getLogger(WelcomeController.class);

	// Feature Flag 키
	private static final String FLAG_KEY = "welcome-page-redesign";

	// 사용자 식별 쿠키 이름
	private static final String USER_ID_COOKIE = "petclinic_user_id";

	private final OpenFeatureAPI openFeatureAPI;

	/**
	 * 생성자 주입
	 */
	public WelcomeController(OpenFeatureAPI openFeatureAPI) {
		this.openFeatureAPI = openFeatureAPI;
	}

	/**
	 * 메인 페이지 핸들러
	 * Feature Flag를 평가하여 A 또는 B 버전을 보여줍니다.
	 */
	@GetMapping("/")
	public String welcome(HttpServletRequest request, HttpServletResponse response, Model model) {

		// 1. 사용자 식별자 가져오기 또는 생성
		String userId = getUserId(request, response);

		// 2. Evaluation Context 생성
		EvaluationContext context = buildEvaluationContext(userId, request);

		// 3. Feature Flag 평가
		Client client = openFeatureAPI.getClient();
		boolean useNewDesign = client.getBooleanValue(FLAG_KEY, false, // 기본값: false (A 버전)
				context);

		// 4. 로깅
		log.debug("User {} → {} (flag={})", userId, useNewDesign ? "Variant B (new)" : "Variant A (old)",
				useNewDesign);

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
		cookie.setMaxAge(365 * 24 * 60 * 60); // 1년
		cookie.setPath("/");
		cookie.setHttpOnly(true);
		response.addCookie(cookie);
		log.debug("Created new user ID: {}", userId);
		return userId;
	}

	/**
	 * Evaluation Context 생성
	 * Feature Flag 평가 시 사용할 컨텍스트 정보를 수집합니다.
	 */
	private EvaluationContext buildEvaluationContext(String userId, HttpServletRequest request) {
		return new MutableContext(userId).add("userAgent", request.getHeader("User-Agent"))
			.add("ipAddress", getClientIp(request))
			.add("referer", request.getHeader("Referer"));
	}

	/**
	 * 클라이언트 IP 주소 가져오기
	 * X-Forwarded-For 헤더를 우선적으로 확인합니다.
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
```

### 3.2 주요 변경사항

1. **OpenFeatureAPI 주입**: 생성자를 통해 주입
2. **사용자 식별**: 쿠키 기반 UUID 생성
3. **Evaluation Context**: 사용자 정보 수집
4. **Flag 평가**: `getBooleanValue()` 호출
5. **모델 속성**: `useNewDesign`, `version` 추가

---

## 4. 템플릿 수정

### 4.1 조건부 렌더링 (권장)

`src/main/resources/templates/welcome.html` 파일을 수정하여 A/B 버전을 조건부로 렌더링합니다.

```html
<!DOCTYPE html>
<html xmlns:th="https://www.thymeleaf.org"
      th:replace="~{fragments/layout :: layout (~{::body},'home')}">

<body>
  <!-- Variant A: 기존 디자인 -->
  <div th:unless="${useNewDesign}">
    <h2 th:text="#{welcome}">Welcome</h2>
    <div class="row">
      <div class="col-md-12">
        <img class="img-responsive"
             src="../static/resources/images/pets.png"
             th:src="@{/resources/images/pets.png}"
             alt="Pets" />
      </div>
    </div>

    <!-- 디버깅용 버전 표시 -->
    <div class="text-muted mt-3" style="font-size: 0.85rem;">
      <small>
        Version: <span th:text="${version}">v1</span> |
        User ID: <span th:text="${#strings.substring(userId, 0, 8)}">12345678</span>
      </small>
    </div>
  </div>

  <!-- Variant B: 새 디자인 -->
  <div th:if="${useNewDesign}">
    <!-- 헤더 섹션 -->
    <div class="text-center mb-4">
      <h1 class="display-4" style="color: #2c3e50; font-weight: 300;">
        🐾 Welcome to PetClinic
      </h1>
      <p class="lead" style="color: #7f8c8d;">
        Compassionate Care for Your Furry Friends
      </p>
    </div>

    <!-- 메인 컨텐츠 -->
    <div class="row">
      <div class="col-md-8 offset-md-2">
        <div class="card shadow-lg border-0">
          <img class="card-img-top"
               src="../static/resources/images/pets.png"
               th:src="@{/resources/images/pets.png}"
               alt="Happy Pets"
               style="border-radius: 0.5rem 0.5rem 0 0;" />

          <div class="card-body text-center py-4">
            <h3 class="card-title mb-3" style="color: #34495e;">
              Your Pet's Health, Our Priority
            </h3>
            <p class="card-text mb-4" style="color: #7f8c8d;">
              Experience world-class veterinary care with our dedicated team
              of professionals. We treat your pets like family.
            </p>

            <!-- CTA 버튼 -->
            <div class="d-grid gap-2 d-md-flex justify-content-md-center">
              <a href="#"
                 th:href="@{/owners/find}"
                 class="btn btn-primary btn-lg px-5">
                Find Your Pet
              </a>
              <a href="#"
                 th:href="@{/vets.html}"
                 class="btn btn-outline-secondary btn-lg px-5">
                Meet Our Vets
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 추가 정보 섹션 -->
    <div class="row mt-5">
      <div class="col-md-4 text-center">
        <div class="mb-3">
          <span style="font-size: 3rem;">🏥</span>
        </div>
        <h5>Expert Care</h5>
        <p class="text-muted">Board-certified veterinarians</p>
      </div>
      <div class="col-md-4 text-center">
        <div class="mb-3">
          <span style="font-size: 3rem;">⏰</span>
        </div>
        <h5>24/7 Available</h5>
        <p class="text-muted">Emergency services anytime</p>
      </div>
      <div class="col-md-4 text-center">
        <div class="mb-3">
          <span style="font-size: 3rem;">❤️</span>
        </div>
        <h5>Compassionate</h5>
        <p class="text-muted">We treat pets like family</p>
      </div>
    </div>

    <!-- 디버깅용 버전 표시 -->
    <div class="text-muted mt-4 text-center" style="font-size: 0.85rem;">
      <small>
        Version: <span th:text="${version}">v2</span> |
        User ID: <span th:text="${#strings.substring(userId, 0, 8)}">12345678</span>
      </small>
    </div>
  </div>
</body>

</html>
```

### 4.2 별도 템플릿 파일 (대안)

Variant B를 별도 파일로 분리하려면:

**1) `src/main/resources/templates/welcome-v2.html` 생성**

```html
<!DOCTYPE html>
<html xmlns:th="https://www.thymeleaf.org"
      th:replace="~{fragments/layout :: layout (~{::body},'home')}">
<body>
  <!-- Variant B 내용만 작성 -->
  <h1 class="display-4">🐾 Welcome to PetClinic</h1>
  <!-- ... -->
</body>
</html>
```

**2) Controller 수정**

```java
@GetMapping("/")
public String welcome(...) {
    // ...
    boolean useNewDesign = client.getBooleanValue(FLAG_KEY, false, context);

    // 템플릿 선택
    return useNewDesign ? "welcome-v2" : "welcome";
}
```

---

## 5. 테스트 작성

### 5.1 단위 테스트

`src/test/java/org/springframework/samples/petclinic/system/WelcomeControllerTests.java` 파일을 생성합니다.

```java
package org.springframework.samples.petclinic.system;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WelcomeController 테스트
 */
@WebMvcTest(WelcomeController.class)
class WelcomeControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private OpenFeatureAPI openFeatureAPI;

	@MockBean
	private Client client;

	/**
	 * Variant A (기존 디자인) 테스트
	 */
	@Test
	void shouldShowVariantA() throws Exception {
		// Given
		when(openFeatureAPI.getClient()).thenReturn(client);
		when(client.getBooleanValue(eq("welcome-page-redesign"), eq(false), any())).thenReturn(false);

		// When & Then
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(view().name("welcome"))
			.andExpect(model().attribute("useNewDesign", false))
			.andExpect(model().attribute("version", "v1"))
			.andExpect(model().attributeExists("userId"));
	}

	/**
	 * Variant B (새 디자인) 테스트
	 */
	@Test
	void shouldShowVariantB() throws Exception {
		// Given
		when(openFeatureAPI.getClient()).thenReturn(client);
		when(client.getBooleanValue(eq("welcome-page-redesign"), eq(false), any())).thenReturn(true);

		// When & Then
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(view().name("welcome"))
			.andExpect(model().attribute("useNewDesign", true))
			.andExpect(model().attribute("version", "v2"))
			.andExpect(model().attributeExists("userId"));
	}

	/**
	 * 사용자 ID 쿠키 생성 테스트
	 */
	@Test
	void shouldCreateUserIdCookie() throws Exception {
		// Given
		when(openFeatureAPI.getClient()).thenReturn(client);
		when(client.getBooleanValue(anyString(), anyBoolean(), any())).thenReturn(false);

		// When & Then
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(cookie().exists("petclinic_user_id"))
			.andExpect(cookie().maxAge("petclinic_user_id", 365 * 24 * 60 * 60))
			.andExpect(cookie().httpOnly("petclinic_user_id", true));
	}

}
```

### 5.2 통합 테스트

`src/test/java/org/springframework/samples/petclinic/system/WelcomeControllerIntegrationTests.java`:

```java
package org.springframework.samples.petclinic.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WelcomeController 통합 테스트
 * 실제 flagd 연결 없이 기본값으로 동작 확인
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WelcomeControllerIntegrationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void shouldReturnWelcomePage() {
		// When
		ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

		// Then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("Welcome");
	}

}
```

### 5.3 테스트 실행

```bash
# 전체 테스트 실행
./mvnw test

# 특정 테스트 클래스만 실행
./mvnw test -Dtest=WelcomeControllerTests

# 테스트 건너뛰고 빌드
./mvnw clean package -DskipTests
```

---

## 6. 로컬 테스트

### 6.1 flagd 로컬 실행

OpenShift 배포 전에 로컬에서 테스트하려면 flagd를 로컬에 실행해야 합니다.

#### 6.1.1 Docker로 flagd 실행

**1) Feature Flag 파일 생성**

`flags.json` 파일을 프로젝트 루트에 생성합니다:

```json
{
  "$schema": "https://flagd.dev/schema/v0/flags.json",
  "flags": {
    "welcome-page-redesign": {
      "state": "ENABLED",
      "variants": {
        "on": true,
        "off": false
      },
      "defaultVariant": "off",
      "targeting": {
        "if": [
          {
            "fractional": [
              { "var": "targetingKey" },
              [
                ["on", 50],
                ["off", 50]
              ]
            ]
          },
          "on",
          "off"
        ]
      }
    }
  }
}
```

**2) flagd 컨테이너 실행**

```bash
# flagd 실행 (flags.json 마운트)
docker run -d \
  --name flagd \
  -p 8013:8013 \
  -v $(pwd)/flags.json:/flags.json \
  ghcr.io/open-feature/flagd:latest \
  start --uri file:./flags.json

# 로그 확인
docker logs -f flagd
```

**3) flagd 상태 확인**

```bash
# Health check
curl http://localhost:8014/healthz

# Flag 목록 조회
curl http://localhost:8014/flags

# 예상 출력:
# {
#   "welcome-page-redesign": {
#     "state": "ENABLED",
#     "variants": {...}
#   }
# }
```

#### 6.1.2 Binary로 flagd 실행 (대안)

```bash
# flagd 다운로드 (macOS)
curl -L https://github.com/open-feature/flagd/releases/latest/download/flagd_Darwin_x86_64.tar.gz | tar -xz

# flagd 실행
./flagd start --uri file:./flags.json
```

### 6.2 Spring Boot 애플리케이션 실행

```bash
# 애플리케이션 실행
./mvnw spring-boot:run

# 또는
./mvnw clean package
java -jar target/spring-petclinic-*.jar
```

### 6.3 브라우저 테스트

1. **브라우저 열기**: http://localhost:8080
2. **여러 번 새로고침**: 쿠키 삭제 후 반복
3. **Variant 확인**: 페이지 하단의 "Version: v1" 또는 "Version: v2" 확인

**쿠키 삭제 방법**:
- Chrome: 개발자 도구 → Application → Cookies → `petclinic_user_id` 삭제
- Firefox: 개발자 도구 → Storage → Cookies → `petclinic_user_id` 삭제

### 6.4 로그 확인

```bash
# 애플리케이션 로그에서 Flag 평가 확인
# 예상 로그:
# DEBUG WelcomeController - User abc123 → Variant A (old) (flag=false)
# DEBUG WelcomeController - User def456 → Variant B (new) (flag=true)
```

### 6.5 Flag 설정 변경 (실시간)

**1) `flags.json` 수정**

```json
{
  "flags": {
    "welcome-page-redesign": {
      "state": "ENABLED",
      "variants": {
        "on": true,
        "off": false
      },
      "defaultVariant": "on",  // 기본값을 "on"으로 변경
      "targeting": {}           // Targeting 제거 (모두 B 버전)
    }
  }
}
```

**2) flagd는 자동으로 파일 변경을 감지하여 즉시 반영**

**3) 브라우저 새로고침**: 모든 사용자가 B 버전 확인

---

## 7. 문제 해결

### 7.1 flagd 연결 실패

**증상**:
```
Failed to initialize OpenFeature provider
Connection refused: localhost:8013
```

**해결**:
1. flagd가 실행 중인지 확인: `docker ps | grep flagd`
2. 포트 확인: `netstat -an | grep 8013`
3. application.properties 확인: `openfeature.flagd.host=localhost`

### 7.2 Flag가 항상 기본값 반환

**원인**:
- Flag 이름 불일치
- Flag가 DISABLED 상태
- Targeting 규칙 오류

**해결**:
```bash
# Flag 목록 확인
curl http://localhost:8014/flags

# Flag 상세 정보 확인
curl http://localhost:8014/flags/welcome-page-redesign
```

### 7.3 테스트 실패

**원인**: OpenFeatureAPI Mock 설정 오류

**해결**:
```java
// Mock 설정 확인
when(openFeatureAPI.getClient()).thenReturn(client);
when(client.getBooleanValue(anyString(), anyBoolean(), any()))
    .thenReturn(true);  // 또는 false
```

---

## 8. 다음 단계

1. ✅ **구현 완료** (현재 문서)
2. ⏭️ **배포 가이드 확인**: [03-deployment-guide.md](03-deployment-guide.md)
3. 🚀 **OpenShift 배포**

---

## 참고 자료

- [OpenFeature Java SDK Reference](https://openfeature.dev/docs/reference/technologies/server/java/)
- [flagd Configuration](https://flagd.dev/reference/flag-definitions/)
- [Spring Boot Tutorial](https://openfeature.dev/docs/tutorials/getting-started/java/spring-boot/)
