# OpenFeature A/B Testing 리서치

> **작성일**: 2025-10-01
> **대상 환경**: OpenShift 4.x + Spring Boot 3.5.0

## 📑 목차

- [1. OpenFeature 개요](#1-openfeature-개요)
- [2. OpenShift Service Mesh vs OpenFeature](#2-openshift-service-mesh-vs-openfeature)
- [3. OpenFeature 아키텍처](#3-openfeature-아키텍처)
- [4. Provider 비교](#4-provider-비교)
- [5. OpenShift 배포 패턴](#5-openshift-배포-패턴)
- [6. A/B 테스트 전략](#6-ab-테스트-전략)

---

## 1. OpenFeature 개요

### 1.1 OpenFeature란?

OpenFeature는 **오픈소스 피처 플래그 표준**으로, 벤더 중립적인 API를 제공합니다.

**핵심 특징**:
- ✅ **벤더 중립**: 다양한 피처 플래그 제공자와 통합 가능
- ✅ **표준화된 API**: 일관된 개발 경험
- ✅ **확장 가능**: 커스텀 프로바이더 구현 가능
- ✅ **오픈소스**: Apache 2.0 라이선스
- ✅ **CNCF 인큐베이팅 프로젝트**: 2023년부터

### 1.2 주요 컴포넌트

```
┌─────────────────────────────────────────────────┐
│         Application Code                        │
│  ┌──────────────────────────────────────────┐   │
│  │  OpenFeature SDK                         │   │
│  │  - Client API                            │   │
│  │  - Evaluation Context                    │   │
│  │  - Hooks                                 │   │
│  └──────────────┬───────────────────────────┘   │
│                 │                                │
│  ┌──────────────▼───────────────────────────┐   │
│  │  Provider (flagd, LaunchDarkly, etc.)    │   │
│  │  - Flag Evaluation Engine                │   │
│  │  - Backend Connection                    │   │
│  └──────────────┬───────────────────────────┘   │
│                 │                                │
└─────────────────┼────────────────────────────────┘
                  │
         ┌────────▼────────┐
         │  Flag Storage   │
         │  - JSON File    │
         │  - Database     │
         │  - Remote API   │
         └─────────────────┘
```

### 1.3 Java SDK 버전 정보 (2025)

- **OpenFeature Java SDK**: v1.15.1
- **flagd Provider**: v0.11.10
- **최소 Java 버전**: Java 8+
- **Spring Boot 통합**: 공식 지원

---

## 2. OpenShift Service Mesh vs OpenFeature

### 2.1 비교표

| 항목 | Service Mesh (Istio) | OpenFeature |
|------|---------------------|-------------|
| **구현 레벨** | 인프라/네트워크 레이어 | 애플리케이션 레이어 |
| **코드 변경** | 최소 또는 불필요 | 필요 (SDK 통합) |
| **트래픽 제어** | VirtualService + DestinationRule | Feature Flag 평가 로직 |
| **라우팅 기준** | HTTP 헤더, 경로, 가중치 | 사용자 속성, 비즈니스 로직 |
| **세그멘테이션** | 제한적 (네트워크 레벨) | 강력함 (애플리케이션 레벨) |
| **부분 기능 토글** | 불가능 | 가능 (UI 요소별 제어) |
| **롤백 속도** | 즉시 (YAML 수정) | 즉시 (CRD 수정, 재배포 불필요) |
| **학습 곡선** | 중간~높음 | 낮음 |
| **운영 복잡도** | 높음 | 낮음 |

### 2.2 장단점 분석

#### Service Mesh 기반 A/B 테스트

**장점**:
- ✅ 애플리케이션 코드와 완전 분리
- ✅ 즉각적인 트래픽 전환
- ✅ Kiali로 실시간 시각화
- ✅ 롤백 즉시 가능 (1분 내)
- ✅ 여러 서비스 동시 제어

**단점**:
- ❌ 비즈니스 로직 기반 제어 어려움
- ❌ 복잡한 사용자 세그멘테이션 제한
- ❌ 부분 기능 토글 불가능
- ❌ 높은 운영 복잡도
- ❌ 추가 인프라 필요 (Istio, Kiali 등)

#### OpenFeature 기반 A/B 테스트

**장점**:
- ✅ 세밀한 사용자 세그멘테이션
- ✅ 비즈니스 로직 기반 제어
- ✅ 부분 기능 토글 가능
- ✅ 다양한 프로바이더 선택
- ✅ 표준화된 API
- ✅ 무료 오픈소스 (flagd)

**단점**:
- ❌ 코드 수정 필요
- ❌ SDK 의존성 추가
- ❌ 초기 학습 필요

### 2.3 권장 사항

**✨ 하이브리드 접근 방식 (Best Practice)**

```
Service Mesh (인프라 레이어)
    ↓ 트래픽 라우팅 & 배포 전략 (Canary, Blue/Green)
OpenFeature (애플리케이션 레이어)
    ↓ 세밀한 기능 제어 & A/B 테스트
```

**Spring PetClinic 프로젝트에는 OpenFeature 권장**:
- 메인 페이지 디자인 A/B 테스트에 최적
- 세밀한 사용자 세그멘테이션 가능
- 낮은 구현 복잡도
- 빠른 시작 가능

---

## 3. OpenFeature 아키텍처

### 3.1 OpenShift 환경 배포 모델

```
┌───────────────────────────────────────────────────────────┐
│                 OpenShift Cluster                         │
│                                                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │  OpenFeature Operator (Namespace: openfeature-*)   │  │
│  │  - FeatureFlag CRD Controller                      │  │
│  │  - FeatureFlagSource CRD Controller                │  │
│  │  - Sidecar Injector                                │  │
│  └────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Application Namespace (petclinic)                 │  │
│  │                                                    │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  Pod: spring-petclinic                       │ │  │
│  │  │  ┌────────────────┐  ┌───────────────────┐  │ │  │
│  │  │  │ Spring Boot    │  │ flagd Sidecar     │  │ │  │
│  │  │  │ Container      │◄─┤ Container         │  │ │  │
│  │  │  │                │  │                   │  │ │  │
│  │  │  │ Port: 8080     │  │ gRPC: 8013       │  │ │  │
│  │  │  │                │  │ HTTP: 8014       │  │ │  │
│  │  │  └────────────────┘  └───────────────────┘  │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  │                                                    │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  FeatureFlag CRD: welcome-flags              │ │  │
│  │  │  - welcome-page-redesign: 50/50 split        │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  │                                                    │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  FeatureFlagSource CRD: petclinic-flags      │ │  │
│  │  │  - source: petclinic/welcome-flags           │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  └────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
```

### 3.2 Request Flow

```
1. 사용자 요청
   ↓
2. OpenShift Route → Service → Pod
   ↓
3. Spring Boot Controller
   - OpenFeatureAPI.getClient()
   - client.getBooleanValue("welcome-page-redesign", false, context)
   ↓
4. flagd Provider → flagd Sidecar (localhost:8013)
   ↓
5. flagd: Flag 평가
   - Targeting Rules 확인
   - Fractional 계산 (50/50)
   - Variant 결정 (on/off)
   ↓
6. 결과 반환 (true/false)
   ↓
7. Controller: 템플릿 선택 (A 또는 B)
   ↓
8. Thymeleaf: HTML 렌더링
   ↓
9. 사용자에게 응답
```

### 3.3 Sidecar Injection 메커니즘

OpenFeature Operator가 Deployment에 다음 어노테이션을 감지하면 자동으로 flagd Sidecar를 주입합니다:

```yaml
metadata:
  annotations:
    openfeature.dev/enabled: "true"
    openfeature.dev/featureflagsource: "petclinic-flags"
```

**주입 결과**:
- flagd 컨테이너 추가
- Volume Mount 설정
- 환경 변수 주입
- InitContainer (선택적)

---

## 4. Provider 비교

### 4.1 주요 Provider 비교표

| Provider | 타입 | SDK 커버리지 | OpenFeature 준수 | 비용 | 추천도 |
|----------|------|-------------|-----------------|------|--------|
| **flagd** | 오픈소스 | 53% | 100% | 무료 | ⭐⭐⭐⭐⭐ |
| **LaunchDarkly** | SaaS | 35% | 높음 | 유료 ($$$) | ⭐⭐⭐⭐ |
| **Split.io** | SaaS | 24% | 중간 | 유료 ($$) | ⭐⭐⭐ |
| **DevCycle** | SaaS | - | 100% | 무료/유료 | ⭐⭐⭐⭐ |
| **GrowthBook** | 오픈소스/SaaS | - | 높음 | 무료/유료 | ⭐⭐⭐⭐ |
| **Unleash** | 오픈소스/SaaS | - | 높음 | 무료/유료 | ⭐⭐⭐⭐ |

### 4.2 flagd 상세 정보 (권장)

**선택 이유**:
1. ✅ **OpenFeature 공식 구현체**: 100% 사양 준수
2. ✅ **무료 오픈소스**: Apache 2.0 라이선스
3. ✅ **Kubernetes 네이티브**: Operator 패턴
4. ✅ **경량**: 메모리 ~50MB, CPU ~0.1 core
5. ✅ **실시간 업데이트**: CRD 변경 시 즉시 반영
6. ✅ **고가용성**: Sidecar 패턴으로 장애 격리

**flagd 특징**:
- **언어**: Go
- **프로토콜**: gRPC, HTTP
- **Flag 저장소**: Kubernetes CRD, JSON 파일, HTTP, gRPC
- **평가 엔진**: JsonLogic 기반
- **모니터링**: Prometheus 메트릭

**flagd vs 상용 서비스**:

| 기능 | flagd | LaunchDarkly | Split.io |
|------|-------|--------------|----------|
| 가격 | 무료 | $8-150/월 | 문의 필요 |
| 관리 UI | ❌ (CRD로 관리) | ✅ (강력) | ✅ |
| 실시간 업데이트 | ✅ | ✅ | ✅ |
| 분석 대시보드 | ❌ (직접 구축) | ✅ | ✅ |
| A/B 테스트 | ✅ | ✅ | ✅ |
| 다중 환경 | ✅ | ✅ | ✅ |
| RBAC | Kubernetes RBAC | ✅ | ✅ |
| 감사 로그 | ❌ | ✅ | ✅ |

**Spring PetClinic에는 flagd 권장**:
- 학습 목적: 무료로 OpenFeature 체험
- OpenShift 환경: Operator로 쉬운 관리
- 간단한 A/B 테스트: flagd로 충분

---

## 5. OpenShift 배포 패턴

### 5.1 Sidecar 패턴 (권장)

```yaml
# Deployment with Sidecar Injection
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-petclinic
  annotations:
    openfeature.dev/enabled: "true"          # Sidecar 주입 활성화
    openfeature.dev/featureflagsource: "petclinic-flags"
spec:
  template:
    spec:
      containers:
      - name: petclinic
        image: quay.io/your-org/spring-petclinic:latest
        ports:
        - containerPort: 8080
      # flagd Sidecar는 Operator가 자동 주입
```

**장점**:
- ✅ 각 Pod마다 독립적인 flagd 인스턴스
- ✅ 네트워크 레이턴시 최소화 (localhost 통신)
- ✅ 장애 격리 (한 Pod 장애가 다른 Pod에 영향 없음)
- ✅ Operator가 자동 관리

**단점**:
- ❌ Pod당 리소스 오버헤드 (~50MB 메모리, ~0.1 CPU)

### 5.2 중앙화 패턴

```yaml
# Standalone flagd Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flagd
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: flagd
        image: ghcr.io/open-feature/flagd:latest
---
# Application connects to flagd Service
```

**장점**:
- ✅ 리소스 효율적 (공유 인스턴스)
- ✅ 중앙 집중식 관리

**단점**:
- ❌ 네트워크 레이턴시 증가
- ❌ SPOF (Single Point of Failure)
- ❌ 확장성 제한

### 5.3 권장 배포 아키텍처

```
┌─────────────────────────────────────────────────────┐
│  OpenShift Project: petclinic                       │
│                                                     │
│  ┌───────────────────────────────────────────────┐ │
│  │  Deployment: spring-petclinic (replicas: 2)  │ │
│  │                                               │ │
│  │  Pod 1:                    Pod 2:            │ │
│  │  ┌─────────┐ ┌────────┐   ┌─────────┐       │ │
│  │  │ App     │ │ flagd  │   │ App     │ ...   │ │
│  │  └─────────┘ └────────┘   └─────────┘       │ │
│  └───────────────────────────────────────────────┘ │
│                                                     │
│  ┌───────────────────────────────────────────────┐ │
│  │  Service: petclinic (ClusterIP)              │ │
│  │  Port: 8080 → App Container                  │ │
│  └───────────────────────────────────────────────┘ │
│                                                     │
│  ┌───────────────────────────────────────────────┐ │
│  │  Route: petclinic.apps.domain.com            │ │
│  │  TLS: Edge Termination                        │ │
│  └───────────────────────────────────────────────┘ │
│                                                     │
│  ┌───────────────────────────────────────────────┐ │
│  │  FeatureFlag CRD: welcome-flags               │ │
│  │  - welcome-page-redesign                      │ │
│  └───────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## 6. A/B 테스트 전략

### 6.1 사용자 세그멘테이션 방법

#### 6.1.1 랜덤 분할 (Fractional)

```yaml
targeting:
  if:
    - fractional:
      - var: targetingKey  # 사용자 ID
      - - on: 50           # 50% → B
        - off: 50          # 50% → A
    - on
    - off
```

**특징**:
- 사용자 ID 기반 해싱
- 일관성 보장 (동일 사용자는 항상 동일 variant)
- 통계적으로 균등 분배

#### 6.1.2 속성 기반 타게팅

```yaml
targeting:
  if:
    - ends_with:
      - var: email
      - "@company.com"
    - on                    # 내부 직원 → B
    - if:
      - in:
        - var: country
        - ["KR", "JP"]
      - on                  # 한국/일본 → B
      - off                 # 나머지 → A
```

**사용 사례**:
- 내부 직원 먼저 테스트
- 지역별 롤아웃
- 베타 테스터 그룹

#### 6.1.3 점진적 롤아웃

```yaml
# Week 1: 10%
targeting:
  if:
    - fractional:
      - var: targetingKey
      - - on: 10
        - off: 90
    - on
    - off

# Week 2: 50%
targeting:
  if:
    - fractional:
      - var: targetingKey
      - - on: 50
        - off: 50
    - on
    - off

# Week 3: 100%
defaultVariant: on
targeting: {}
```

### 6.2 Evaluation Context 설계

#### 6.2.1 필수 속성

```java
EvaluationContext context = new MutableContext(userId)
    .add("email", user.getEmail())
    .add("country", getCountryFromIp(request))
    .add("userAgent", request.getHeader("User-Agent"))
    .add("timestamp", Instant.now().toString());
```

#### 6.2.2 선택 속성

```java
context
    .add("isNewUser", isNewUser(userId))
    .add("accountAge", getAccountAge(userId))
    .add("plan", user.getSubscriptionPlan())
    .add("device", detectDevice(request));
```

### 6.3 메트릭 수집 전략

#### 6.3.1 기본 메트릭

- **노출 수**: 각 variant 노출 횟수
- **전환율**: 목표 달성 비율 (예: 버튼 클릭)
- **에러율**: variant별 에러 발생률
- **응답 시간**: variant별 페이지 로딩 시간

#### 6.3.2 비즈니스 메트릭

- **사용자 체류 시간**: 페이지 머문 시간
- **이탈률**: 즉시 나간 사용자 비율
- **다음 액션**: "Find Owners" 클릭률

### 6.4 통계적 유의성 검증

**최소 요구사항**:
- **샘플 사이즈**: 각 variant당 최소 1,000명
- **테스트 기간**: 최소 1주일 (주중/주말 포함)
- **신뢰 수준**: 95% (p-value < 0.05)
- **검정력**: 80% (β = 0.2)

**계산 도구**:
- [Evan's Awesome A/B Tools](https://www.evanmiller.org/ab-testing/)
- [Optimizely Sample Size Calculator](https://www.optimizely.com/sample-size-calculator/)

---

## 7. 성능 및 리소스

### 7.1 flagd Sidecar 리소스

```yaml
resources:
  requests:
    memory: "32Mi"
    cpu: "50m"
  limits:
    memory: "64Mi"
    cpu: "100m"
```

### 7.2 레이턴시

- **Flag 평가**: < 1ms (로컬 평가)
- **gRPC 통신**: < 1ms (localhost)
- **전체 오버헤드**: < 2ms

### 7.3 확장성

- **Sidecar 패턴**: Pod 수에 비례하여 확장
- **동시 평가**: 제한 없음 (로컬 평가)

---

## 8. 보안 고려사항

### 8.1 통신 보안

- **App ↔ flagd**: localhost 통신 (Pod 내부)
- **flagd ↔ Kubernetes API**: ServiceAccount Token 사용
- **외부 노출**: 없음 (ClusterIP)

### 8.2 접근 제어

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: feature-flag-admin
rules:
- apiGroups: ["core.openfeature.dev"]
  resources: ["featureflags", "featureflagsources"]
  verbs: ["get", "list", "create", "update", "patch", "delete"]
```

### 8.3 민감 정보

- ❌ Flag에 비밀번호, API 키 저장 금지
- ✅ Flag는 boolean, string, number만 사용
- ✅ 민감한 사용자 정보는 해싱 후 targetingKey로 사용

---

## 9. 다음 단계

1. ✅ **리서치 완료** (현재 문서)
2. ⏭️ **구현 가이드 확인**: [02-implementation-guide.md](02-implementation-guide.md)
3. ⏭️ **배포 가이드 확인**: [03-deployment-guide.md](03-deployment-guide.md)
4. 🚀 **구현 시작**

---

## 참고 자료

- [OpenFeature 공식 문서](https://openfeature.dev/)
- [flagd 공식 문서](https://flagd.dev/)
- [OpenFeature Java SDK](https://github.com/open-feature/java-sdk)
- [OpenFeature Operator](https://github.com/open-feature/open-feature-operator)
- [Spring Boot Tutorial](https://openfeature.dev/docs/tutorials/getting-started/java/spring-boot/)
