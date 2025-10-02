# OpenShift 배포 계획서 - OpenFeature 통합

> **작성일**: 2025-10-02
> **대상 환경**: OpenShift 4.x
> **Namespace**: open-feature-test
> **상태**: 📋 계획 수립 완료

## 📑 목차

- [1. 현황 분석](#1-현황-분석)
- [2. 배포 아키텍처](#2-배포-아키텍처)
- [3. 배포 전략](#3-배포-전략)
- [4. 리소스 정의](#4-리소스-정의)
- [5. 배포 순서](#5-배포-순서)
- [6. 검증 계획](#6-검증-계획)
- [7. 롤백 계획](#7-롤백-계획)
- [8. 타임라인](#8-타임라인)

---

## 1. 현황 분석

### 1.1 기존 배포 상태

#### 현재 리소스
```
Namespace: open-feature-test
├── Deployment: spring-petclinic (2 replicas)
│   └── Container: petclinic
│       ├── Image: image-registry.openshift-image-registry.svc:5000/open-feature-test/spring-petclinic:v1.0.0
│       ├── Port: 8080
│       └── Resources: 512Mi-1Gi / 500m-1000m
├── Service: spring-petclinic (ClusterIP:8080)
└── Route: spring-petclinic (HTTPS, edge termination)
```

#### 기존 Deployment 특징
- **OpenFeature 미적용**: 단일 컨테이너 구조
- **환경 변수**: JAVA_OPTS만 설정
- **Health Check**: Actuator 기반 liveness/readiness
- **리소스**: 적절하게 설정됨

### 1.2 변경 필요 사항

| 항목 | 현재 상태 | 목표 상태 |
|------|----------|----------|
| 컨테이너 수 | 1개 (petclinic) | 2개 (petclinic + flagd sidecar) |
| OpenFeature 설정 | 없음 | localhost:8013 연결 |
| Annotations | 없음 | OpenFeature Operator 어노테이션 추가 |
| 환경 변수 | JAVA_OPTS | JAVA_OPTS + OpenFeature 설정 |
| Feature Flags | 없음 | FeatureFlag CRD 생성 |

---

## 2. 배포 아키텍처

### 2.1 최종 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    OpenShift Cluster                        │
│                                                             │
│  ┌───────────────────────────────────────────────────┐     │
│  │  Namespace: open-feature-test                     │     │
│  │                                                   │     │
│  │  ┌─────────────────────────────────────────┐     │     │
│  │  │  Pod: spring-petclinic-xxxxx           │     │     │
│  │  │                                         │     │     │
│  │  │  ┌──────────────────┐  ┌────────────┐  │     │     │
│  │  │  │   petclinic      │  │   flagd    │  │     │     │
│  │  │  │   (Spring Boot)  │  │ (Sidecar)  │  │     │     │
│  │  │  │                  │  │            │  │     │     │
│  │  │  │   Port: 8080     │  │ Port: 8013 │  │     │     │
│  │  │  │                  │  │ Port: 8014 │  │     │     │
│  │  │  │  ┌────────────┐  │  │   (gRPC)   │  │     │     │
│  │  │  │  │OpenFeature │  │  │   (HTTP)   │  │     │     │
│  │  │  │  │   Client   │──┼──┼────────────┤  │     │     │
│  │  │  │  └────────────┘  │  │            │  │     │     │
│  │  │  │                  │  │ ┌────────┐ │  │     │     │
│  │  │  │                  │  │ │ Flags  │ │  │     │     │
│  │  │  │                  │  │ └────────┘ │  │     │     │
│  │  │  └──────────────────┘  └────────────┘  │     │     │
│  │  │         localhost 통신                  │     │     │
│  │  └─────────────────────────────────────────┘     │     │
│  │                                                   │     │
│  │  ┌─────────────────────────────────────────┐     │     │
│  │  │  ConfigMap: feature-flags              │     │     │
│  │  │  - welcome-page-redesign (50/50)       │     │     │
│  │  └─────────────────────────────────────────┘     │     │
│  │                      ↓                            │     │
│  │  ┌─────────────────────────────────────────┐     │     │
│  │  │  FeatureFlagSource                      │     │     │
│  │  │  - name: petclinic-flags                │     │     │
│  │  └─────────────────────────────────────────┘     │     │
│  │                                                   │     │
│  │  ┌─────────────────────────────────────────┐     │     │
│  │  │  Service: spring-petclinic              │     │     │
│  │  │  - ClusterIP: 8080                      │     │     │
│  │  └─────────────────────────────────────────┘     │     │
│  │                      ↓                            │     │
│  │  ┌─────────────────────────────────────────┐     │     │
│  │  │  Route: spring-petclinic                │     │     │
│  │  │  - HTTPS (Edge Termination)             │     │     │
│  │  └─────────────────────────────────────────┘     │     │
│  └───────────────────────────────────────────────────┘     │
│                                                             │
│  ┌───────────────────────────────────────────────────┐     │
│  │  Namespace: openfeature-operator-system           │     │
│  │  ┌─────────────────────────────────────────┐      │     │
│  │  │  OpenFeature Operator                   │      │     │
│  │  │  - Sidecar Injection                    │      │     │
│  │  │  - FeatureFlag Management               │      │     │
│  │  └─────────────────────────────────────────┘      │     │
│  └───────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 통신 흐름

```
사용자 요청
    ↓
Route (HTTPS)
    ↓
Service (ClusterIP:8080)
    ↓
Pod: spring-petclinic
    ↓
Container: petclinic (Spring Boot)
    ↓
OpenFeature Client
    ↓
gRPC (localhost:8013)
    ↓
Container: flagd (Sidecar)
    ↓
FeatureFlag CR
    ↓
Feature Flag 평가 결과 반환
    ↓
WelcomeController
    ↓
Thymeleaf (A/B 렌더링)
    ↓
사용자에게 응답
```

---

## 3. 배포 전략

### 3.1 배포 방식: Rolling Update (무중단 배포)

#### 장점
- ✅ 서비스 중단 없음
- ✅ 점진적 롤아웃
- ✅ 문제 발생 시 자동 롤백

#### 설정
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1        # 동시에 1개 추가 Pod 생성 가능
    maxUnavailable: 0  # 항상 최소 2개 Pod 유지
```

### 3.2 단계별 배포 계획

#### Phase 1: 사전 준비 (30분)
1. OpenFeature Operator 설치 확인
2. CRD 확인
3. 이미지 빌드 및 푸시

#### Phase 2: CR 배포 (10분)
1. FeatureFlagSource 배포
2. FeatureFlag CR 배포 (초기: 50% B 버전)

#### Phase 3: Deployment 수정 (20분)
1. 기존 Deployment Backup
2. Deployment 수정 (어노테이션 + 환경 변수)
3. Rolling Update 실행
4. Sidecar 주입 확인

#### Phase 4: 검증 (30분)
1. Pod 상태 확인 (2/2 Running)
2. 로그 확인 (OpenFeature 초기화)
3. Route 접속 테스트
4. A/B 버전 확인

#### Phase 5: 모니터링 (지속)
1. 메트릭 수집
2. 에러율 모니터링
3. 점진적 트래픽 증가

---

## 4. 리소스 정의

### 4.1 디렉토리 구조

```
docs/openfeature/manifests/
├── 00-namespace.yaml          # (선택) Namespace 생성
├── 01-featureflagsource.yaml  # FeatureFlagSource CR
├── 02-featureflag.yaml        # FeatureFlag CR (50/50 A/B)
├── 03-deployment.yaml         # 수정된 Deployment
├── 04-service.yaml            # 기존 Service (변경 없음)
└── 05-route.yaml              # 기존 Route (변경 없음)
```

**주요 변경**: ConfigMap 방식 제거, **FeatureFlag CR 방식만 사용** (OpenShift Native)

### 4.2 FeatureFlagSource

**파일**: `01-featureflagsource.yaml`

```yaml
apiVersion: core.openfeature.dev/v1beta1
kind: FeatureFlagSource
metadata:
  name: petclinic-flags
  namespace: open-feature-test
  labels:
    app: petclinic
spec:
  sources:
  - source: welcome-flags
    provider: kubernetes
  evaluator: json
```

**설명**:
- FeatureFlag CR `welcome-flags`를 소스로 사용
- Kubernetes Provider 사용 (CR Native)
- JSON 형식으로 평가
- **ConfigMap 방식 대신 CR 직접 참조**

### 4.3 FeatureFlag CR ⭐ **핵심 리소스**

**파일**: `02-featureflag.yaml`

```yaml
apiVersion: core.openfeature.dev/v1beta1
kind: FeatureFlag
metadata:
  name: welcome-flags
  namespace: open-feature-test
  labels:
    app: petclinic
    version: v1.0.0
spec:
  flagSpec:
    flags:
      welcome-page-redesign:
        state: ENABLED
        variants:
          on: true
          off: false
        defaultVariant: off
        targeting:
          if:
          - fractional:
            - var: targetingKey
            - - on
              - 50
            - - off
              - 50
          - on
          - off
```

**설명**:
- **Kubernetes Native**: CR로 직접 정의 (ConfigMap 방식 제거)
- **50/50 A/B 테스트**: 사용자 ID 기반 일관된 분배
- **실시간 업데이트**: Watch API로 즉각 반영 (< 1초)
- **타입 안정성**: CRD 스키마 검증
- **GitOps 친화적**: ArgoCD/Flux 완벽 호환
- **RBAC 통합**: Kubernetes RBAC로 권한 관리

**주요 필드**:
- `state`: ENABLED (활성화) / DISABLED (비활성화)
- `variants`: Flag 값 정의 (on=true, off=false)
- `defaultVariant`: 기본값 (off = A 버전)
- `targeting`: 타게팅 룰 (fractional = 확률 기반 분배)

### 4.4 Deployment (수정)

**파일**: `03-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-petclinic
  namespace: open-feature-test
  labels:
    app: petclinic
    app.kubernetes.io/name: petclinic
    app.kubernetes.io/version: "1.0.0"
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: petclinic
  template:
    metadata:
      labels:
        app: petclinic
      annotations:
        # 🆕 OpenFeature Operator - Sidecar Injection
        openfeature.dev/enabled: "true"
        openfeature.dev/featureflagsource: "petclinic-flags"
    spec:
      containers:
      - name: petclinic
        image: image-registry.openshift-image-registry.svc:5000/open-feature-test/spring-petclinic:v1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          protocol: TCP
          name: http
        env:
        - name: JAVA_OPTS
          value: "-Xmx512m -Xms256m"
        # 🆕 OpenFeature 환경 변수
        - name: OPENFEATURE_FLAGD_HOST
          value: "localhost"  # Sidecar는 localhost
        - name: OPENFEATURE_FLAGD_PORT
          value: "8013"
        - name: LOGGING_LEVEL_DEV_OPENFEATURE
          value: "DEBUG"
        - name: LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SAMPLES_PETCLINIC_SYSTEM_WELCOMECONTROLLER
          value: "DEBUG"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
      # flagd Sidecar는 OpenFeature Operator가 자동 주입
      # 주입되는 컨테이너:
      # - name: flagd
      #   image: ghcr.io/open-feature/flagd:latest
      #   ports:
      #   - containerPort: 8013 (gRPC)
      #   - containerPort: 8014 (HTTP/Metrics)
```

**주요 변경사항**:
1. **Annotations 추가**:
   - `openfeature.dev/enabled: "true"` - Sidecar 주입 활성화
   - `openfeature.dev/featureflagsource: "petclinic-flags"` - Flag 소스 지정

2. **환경 변수 추가**:
   - `OPENFEATURE_FLAGD_HOST=localhost` - Sidecar 연결
   - `OPENFEATURE_FLAGD_PORT=8013` - gRPC 포트
   - 로깅 레벨 설정

3. **Strategy 명시**:
   - Rolling Update 설정 추가

### 4.5 Service (변경 없음)

**파일**: `04-service.yaml`

기존 Service 그대로 사용:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: spring-petclinic
  namespace: open-feature-test
  labels:
    app: petclinic
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: petclinic
```

### 4.6 Route (변경 없음)

**파일**: `05-route.yaml`

기존 Route 그대로 사용:

```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: spring-petclinic
  namespace: open-feature-test
  labels:
    app: petclinic
spec:
  to:
    kind: Service
    name: spring-petclinic
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  wildcardPolicy: None
```

---

## 5. 배포 순서

### 5.1 사전 준비

#### Step 1: OpenFeature Operator 설치 확인

```bash
# Operator 설치 확인
oc get pods -n openfeature-operator-system

# 예상 출력:
# NAME                                                      READY   STATUS
# openfeature-operator-controller-manager-xxxxx-xxxxx      2/2     Running

# CRD 확인
oc get crd | grep openfeature

# 예상 출력:
# featureflags.core.openfeature.dev
# featureflagsources.core.openfeature.dev
```

**Operator가 없으면**:

```bash
# Helm으로 설치
helm repo add openfeature https://open-feature.github.io/open-feature-operator/
helm repo update
helm install openfeature-operator openfeature/open-feature-operator \
  --namespace openfeature-operator-system \
  --create-namespace

# 설치 확인
oc get pods -n openfeature-operator-system
```

#### Step 2: 이미지 빌드 및 푸시

```bash
# Maven 빌드
./mvnw clean package -DskipTests

# 이미지 빌드 (OpenShift BuildConfig 사용)
oc start-build spring-petclinic \
  --from-dir=. \
  --follow \
  -n open-feature-test

# 또는 로컬 빌드 후 푸시
docker build -t image-registry.openshift-image-registry.svc:5000/open-feature-test/spring-petclinic:v1.0.0 .
docker push image-registry.openshift-image-registry.svc:5000/open-feature-test/spring-petclinic:v1.0.0
```

#### Step 3: 기존 Deployment Backup

```bash
# 현재 Deployment 백업
oc get deployment spring-petclinic -n open-feature-test -o yaml > deployment-backup.yaml

# 현재 상태 확인
oc get all -n open-feature-test
```

### 5.2 리소스 배포

#### Step 1: FeatureFlagSource 배포

```bash
# FeatureFlagSource 생성
oc apply -f docs/openfeature/manifests/01-featureflagsource.yaml

# 확인
oc get featureflagsource -n open-feature-test
oc describe featureflagsource petclinic-flags -n open-feature-test
```

#### Step 2: FeatureFlag CR 배포 ⭐

```bash
# FeatureFlag 생성 (50/50 A/B 테스트)
oc apply -f docs/openfeature/manifests/02-featureflag.yaml

# 확인
oc get featureflag -n open-feature-test
oc describe featureflag welcome-flags -n open-feature-test

# Flag 상세 정보
oc get featureflag welcome-flags -n open-feature-test -o yaml
```

#### Step 3: Deployment 수정

```bash
# 수정된 Deployment 배포
oc apply -f docs/openfeature/manifests/03-deployment.yaml

# Rolling Update 진행 상황 확인
oc rollout status deployment/spring-petclinic -n open-feature-test

# 예상 출력:
# Waiting for deployment "spring-petclinic" rollout to finish: 1 out of 2 new replicas have been updated...
# Waiting for deployment "spring-petclinic" rollout to finish: 1 old replicas are pending termination...
# deployment "spring-petclinic" successfully rolled out
```

#### Step 4: Pod 상태 확인

```bash
# Pod 목록
oc get pods -n open-feature-test

# 예상 출력 (Sidecar 주입 후):
# NAME                                READY   STATUS    RESTARTS   AGE
# spring-petclinic-xxxxx-xxxxx        2/2     Running   0          2m
# spring-petclinic-yyyyy-yyyyy        2/2     Running   0          1m

# Pod 상세 정보
oc describe pod -n open-feature-test -l app=petclinic | grep -A 10 "Containers:"

# 예상 출력:
# Containers:
#   petclinic:
#     Container ID:   cri-o://...
#     Image:          image-registry.openshift-image-registry.svc:5000/open-feature-test/spring-petclinic:v1.0.0
#     Port:           8080/TCP
#   flagd:
#     Container ID:   cri-o://...
#     Image:          ghcr.io/open-feature/flagd:latest
#     Ports:          8013/TCP, 8014/TCP
```

---

## 6. 검증 계획

### 6.1 Pod 레벨 검증

#### 1) 컨테이너 상태 확인

```bash
# 모든 컨테이너 Running 확인
oc get pods -n open-feature-test -l app=petclinic

# 각 Pod는 2/2 READY 상태여야 함
```

#### 2) 로그 확인

```bash
# Spring Boot 로그
oc logs -n open-feature-test -l app=petclinic -c petclinic --tail=50

# 예상 로그:
# ✅ OpenFeature initialized successfully
#    Provider: flagd
#    Host: localhost
#    Port: 8013

# flagd 로그
oc logs -n open-feature-test -l app=petclinic -c flagd --tail=50

# 예상 로그:
# flagd started successfully
# Loaded feature flags from source: petclinic-flags
# Flag: welcome-page-redesign (ENABLED)
```

#### 3) flagd 연결 테스트

```bash
# Pod 이름 가져오기
POD_NAME=$(oc get pods -n open-feature-test -l app=petclinic -o jsonpath='{.items[0].metadata.name}')

# flagd Health Check
oc exec -n open-feature-test $POD_NAME -c flagd -- \
  curl -s http://localhost:8014/healthz

# 예상 출력:
# OK

# Flag 목록 조회
oc exec -n open-feature-test $POD_NAME -c flagd -- \
  curl -s http://localhost:8014/flags | jq .

# 예상 출력:
# {
#   "welcome-page-redesign": {
#     "state": "ENABLED",
#     "variants": {...},
#     "defaultVariant": "off"
#   }
# }
```

### 6.2 서비스 레벨 검증

#### 1) Route 접속 테스트

```bash
# Route 주소 확인
ROUTE=$(oc get route spring-petclinic -n open-feature-test -o jsonpath='{.spec.host}')
echo "Route: https://$ROUTE"

# Health Check
curl -k https://$ROUTE/actuator/health

# 예상 출력:
# {"status":"UP"}
```

#### 2) 브라우저 테스트

```bash
# 브라우저에서 접속
open https://$ROUTE

# 확인 사항:
# 1. 페이지가 정상적으로 로드되는지
# 2. 하단에 "Version: v1" 또는 "Version: v2" 표시 확인
# 3. User ID 표시 확인
# 4. 쿠키 삭제 후 여러 번 새로고침하여 A/B 버전 변경 확인
```

#### 3) A/B 버전 분포 확인

```bash
# 10번 요청하여 버전 확인
for i in {1..10}; do
  curl -k -s https://$ROUTE \
    -c cookie-$i.txt \
    | grep -o 'Version: v[12]'
done

# 예상 출력 (50% B 설정 시):
# Version: v1
# Version: v2
# Version: v1
# Version: v2
# Version: v1
# Version: v2
# Version: v1
# Version: v1
# Version: v2
# Version: v2  ← 약 50% B 버전
```

### 6.3 메트릭 검증

```bash
# flagd 메트릭 조회
oc exec -n open-feature-test $POD_NAME -c flagd -- \
  curl -s http://localhost:8014/metrics | grep openfeature

# 예상 출력:
# openfeature_flag_evaluations_total{flag="welcome-page-redesign",variant="on"} 5
# openfeature_flag_evaluations_total{flag="welcome-page-redesign",variant="off"} 45
```

---

## 7. 롤백 계획

### 7.1 긴급 롤백 (즉시)

#### 방법 1: Flag Disable

```bash
# FeatureFlag 상태를 DISABLED로 변경
oc patch featureflag welcome-flags -n open-feature-test \
  --type=merge \
  -p '{"spec":{"flagSpec":{"flags":{"welcome-page-redesign":{"state":"DISABLED"}}}}}'

# 또는 ConfigMap 수정 (ConfigMap 방식 사용 시)
oc edit configmap feature-flags -n open-feature-test
# state: "DISABLED"로 변경
```

**효과**: 모든 사용자가 기본값(A 버전) 사용

#### 방법 2: Deployment 롤백

```bash
# 이전 버전으로 롤백
oc rollout undo deployment/spring-petclinic -n open-feature-test

# 롤백 상태 확인
oc rollout status deployment/spring-petclinic -n open-feature-test
```

**효과**: OpenFeature 적용 전 버전으로 복원

#### 방법 3: Backup에서 복원

```bash
# 백업된 Deployment 적용
oc apply -f deployment-backup.yaml

# 강제 재시작
oc rollout restart deployment/spring-petclinic -n open-feature-test
```

### 7.2 단계별 롤백

#### 문제 시나리오별 대응

| 문제 | 증상 | 대응 |
|------|------|------|
| Sidecar 주입 실패 | Pod 1/1 (flagd 없음) | Operator 확인 후 Deployment 재배포 |
| flagd 연결 실패 | "Connection refused" 에러 | 환경 변수 확인, flagd 로그 확인 |
| Flag 평가 실패 | 항상 기본값 | FeatureFlag/ConfigMap 확인 |
| 성능 저하 | 응답 시간 증가 | 리소스 확인, flagd 메트릭 확인 |
| 높은 에러율 | 5xx 에러 증가 | 즉시 Flag Disable 또는 롤백 |

---

## 8. 타임라인

### 8.1 배포 일정

| 날짜 | 단계 | 작업 내용 | 담당 | 상태 |
|------|------|----------|------|------|
| Day 1 | 사전 준비 | Operator 설치, 이미지 빌드 | DevOps | ⏳ 대기 |
| Day 1 | CR 배포 | FeatureFlagSource, FeatureFlag 배포 | DevOps | ⏳ 대기 |
| Day 1 | Deployment 수정 | Rolling Update 실행 | DevOps | ⏳ 대기 |
| Day 1 | 검증 | Pod, 서비스, A/B 테스트 | QA | ⏳ 대기 |
| Day 2-4 | 모니터링 (50%) | 메트릭, 로그, 에러율 확인 | DevOps | ⏳ 대기 |
| Day 5 | 의사결정 | 100% 확대 또는 롤백 | 팀 | ⏳ 대기 |
| Day 6 | 트래픽 증가 (100%) | Flag 수정 (100/0) | DevOps | ⏳ 대기 |
| Day 7-9 | 모니터링 (50%) | A/B 성능 비교 | QA | ⏳ 대기 |
| Day 10 | 최종 결정 | 100% B 또는 롤백 | 팀 | ⏳ 대기 |

### 8.2 점진적 롤아웃 계획

#### Week 1: 50% B 버전 (초기 배포)

```yaml
targeting:
  if:
  - fractional:
    - var: targetingKey
    - - on
      - 50    # 50% B
    - - off
      - 50    # 50% A
  - on
  - off
```

**모니터링 메트릭**:
- 에러율 < 1%
- 평균 응답 시간 < 200ms
- 사용자 이탈률 변화 < 5%
- A/B 성능 비교 (전환율, 체류시간 등)

#### Week 2: 100% B 버전 (성공 시)

```yaml
defaultVariant: on
targeting: {}  # Targeting 제거
```

또는:

```bash
oc patch featureflag welcome-flags -n open-feature-test \
  --type=merge \
  -p '{"spec":{"flagSpec":{"flags":{"welcome-page-redesign":{"defaultVariant":"on","targeting":{}}}}}}'
```

---

## 9. 체크리스트

### 9.1 배포 전 체크리스트

- [ ] OpenFeature Operator 설치 확인
- [ ] CRD 확인 (FeatureFlag, FeatureFlagSource)
- [ ] 이미지 빌드 및 푸시 완료
- [ ] 기존 Deployment 백업
- [ ] Manifest 파일 검토
- [ ] 롤백 계획 수립

### 9.2 배포 체크리스트

- [ ] FeatureFlagSource 배포
- [ ] FeatureFlag CR 배포 (50% B)
- [ ] Deployment 수정 배포
- [ ] Rolling Update 완료
- [ ] Pod 상태 확인 (2/2 Running)
- [ ] Sidecar 주입 확인

### 9.3 검증 체크리스트

- [ ] Spring Boot 로그 확인 (OpenFeature 초기화)
- [ ] flagd 로그 확인 (Flag 로드)
- [ ] flagd Health Check
- [ ] Route 접속 테스트
- [ ] A/B 버전 모두 동작 확인
- [ ] 쿠키 생성 확인 (5분 유효기간)
- [ ] 메트릭 수집 확인

### 9.4 모니터링 체크리스트

- [ ] 에러율 모니터링 (< 1%)
- [ ] 응답 시간 모니터링 (< 200ms)
- [ ] Flag 평가 횟수 확인
- [ ] A/B 버전 분포 확인 (50% vs 50%)
- [ ] 사용자 피드백 수집

---

## 10. 참고 자료

### 10.1 내부 문서
- [01-research.md](01-research.md) - OpenFeature 조사
- [02-implementation-guide.md](02-implementation-guide.md) - 구현 가이드
- [04-implementation-completed.md](04-implementation-completed.md) - 구현 완료 보고서

### 10.2 외부 문서
- [OpenFeature Operator Quickstart](https://openfeature.dev/docs/tutorials/open-feature-operator/quick-start/)
- [flagd Configuration](https://flagd.dev/reference/flag-definitions/)
- [OpenShift Deployment Strategies](https://docs.openshift.com/container-platform/latest/applications/deployments/deployment-strategies.html)

---

## 11. 다음 단계

1. ✅ **계획 수립 완료** (현재 문서)
2. 📝 **Manifest 파일 생성** - `docs/openfeature/manifests/` 디렉토리에 YAML 생성
3. 🚀 **배포 실행** - 단계별 배포 진행
4. ✅ **검증** - 체크리스트에 따라 검증
5. 📊 **모니터링** - 메트릭 수집 및 분석
6. 🎯 **점진적 롤아웃** - 50% → 100% (성공 시)

---

**작성자**: AI Assistant
**검토 필요**: DevOps 팀, QA 팀
**승인 필요**: Tech Lead
