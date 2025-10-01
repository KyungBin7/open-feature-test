# OpenShift 배포 가이드

> **작성일**: 2025-10-01
> **대상 환경**: OpenShift 4.x
> **난이도**: 중급

## 📑 목차

- [1. 사전 준비](#1-사전-준비)
- [2. OpenFeature Operator 설치](#2-openfeature-operator-설치)
- [3. 애플리케이션 이미지 빌드](#3-애플리케이션-이미지-빌드)
- [4. OpenShift 리소스 배포](#4-openshift-리소스-배포)
- [5. Feature Flag 정의](#5-feature-flag-정의)
- [6. 배포 검증](#6-배포-검증)
- [7. A/B 테스트 실행](#7-ab-테스트-실행)
- [8. 모니터링](#8-모니터링)
- [9. 롤백 및 문제 해결](#9-롤백-및-문제-해결)

---

## 1. 사전 준비

### 1.1 필수 도구

```bash
# oc CLI 설치 확인
oc version

# kubectl 설치 확인 (선택)
kubectl version --client

# Helm 설치 확인
helm version
```

### 1.2 OpenShift 클러스터 접근

```bash
# OpenShift 로그인
oc login --server=https://api.your-cluster.com:6443 \
  --username=your-username \
  --password=your-password

# 또는 토큰으로 로그인
oc login --server=https://api.your-cluster.com:6443 \
  --token=sha256~your-token

# 현재 사용자 확인
oc whoami

# 클러스터 정보 확인
oc cluster-info
```

### 1.3 Namespace 생성

```bash
# Namespace(Project) 생성
oc new-project petclinic

# 또는
oc create namespace petclinic

# Namespace 확인
oc get projects | grep petclinic
```

### 1.4 이미지 레지스트리 설정

OpenShift 내부 레지스트리 또는 외부 레지스트리 (Quay.io, Docker Hub) 사용 가능합니다.

**Quay.io 사용 예시**:

```bash
# Quay.io 로그인
docker login quay.io

# 또는 Podman
podman login quay.io

# Secret 생성 (Private 레지스트리인 경우)
oc create secret docker-registry quay-secret \
  --docker-server=quay.io \
  --docker-username=your-username \
  --docker-password=your-password \
  --docker-email=your-email@example.com \
  -n petclinic

# Service Account에 Secret 연결
oc secrets link default quay-secret --for=pull -n petclinic
```

---

## 2. OpenFeature Operator 설치

### 2.1 Helm으로 설치 (권장)

```bash
# Helm Repository 추가
helm repo add openfeature https://open-feature.github.io/open-feature-operator/
helm repo update

# OpenFeature Operator 설치
helm install openfeature-operator openfeature/open-feature-operator \
  --namespace openfeature-operator-system \
  --create-namespace

# 설치 확인
oc get pods -n openfeature-operator-system

# 예상 출력:
# NAME                                                      READY   STATUS
# openfeature-operator-controller-manager-xxxxx-xxxxx      2/2     Running
```

### 2.2 Manifest로 설치 (대안)

```bash
# Operator 설치
oc apply -f https://github.com/open-feature/open-feature-operator/releases/download/v0.6.0/release.yaml

# 설치 확인
oc get pods -n open-feature-operator-system
```

### 2.3 CRD 확인

```bash
# FeatureFlag CRD 확인
oc get crd featureflags.core.openfeature.dev

# FeatureFlagSource CRD 확인
oc get crd featureflagsources.core.openfeature.dev

# CRD 상세 정보
oc describe crd featureflags.core.openfeature.dev
```

---

## 3. 애플리케이션 이미지 빌드

### 3.1 Spring Boot 이미지 빌드

```bash
# Maven으로 이미지 빌드 (Cloud Native Buildpacks)
./mvnw spring-boot:build-image

# 생성된 이미지 확인
docker images | grep petclinic

# 예상 출력:
# spring-petclinic   3.5.0-SNAPSHOT   xxxxx   2 minutes ago   345MB
```

### 3.2 이미지 태그 및 푸시

```bash
# 이미지 태그
docker tag spring-petclinic:3.5.0-SNAPSHOT \
  quay.io/your-org/spring-petclinic:latest

docker tag spring-petclinic:3.5.0-SNAPSHOT \
  quay.io/your-org/spring-petclinic:v1.0.0

# 이미지 푸시
docker push quay.io/your-org/spring-petclinic:latest
docker push quay.io/your-org/spring-petclinic:v1.0.0

# 푸시 확인
# Quay.io 웹 UI에서 확인: https://quay.io/repository/your-org/spring-petclinic
```

### 3.3 Dockerfile 사용 (대안)

프로젝트 루트에 `Dockerfile` 생성:

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# JAR 빌드
./mvnw clean package -DskipTests

# Docker 이미지 빌드
docker build -t quay.io/your-org/spring-petclinic:latest .

# 푸시
docker push quay.io/your-org/spring-petclinic:latest
```

---

## 4. OpenShift 리소스 배포

### 4.1 디렉토리 구조

```
openshift/
├── 01-deployment.yaml
├── 02-service.yaml
├── 03-route.yaml
├── 04-featureflagsource.yaml
└── 05-featureflag.yaml
```

### 4.2 Deployment

`openshift/01-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-petclinic
  namespace: petclinic
  labels:
    app: petclinic
    app.kubernetes.io/name: petclinic
    app.kubernetes.io/version: "1.0.0"
spec:
  replicas: 2
  selector:
    matchLabels:
      app: petclinic
  template:
    metadata:
      labels:
        app: petclinic
      annotations:
        # OpenFeature Operator - Sidecar Injection
        openfeature.dev/enabled: "true"
        openfeature.dev/featureflagsource: "petclinic-flags"
    spec:
      containers:
      - name: petclinic
        image: quay.io/your-org/spring-petclinic:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          protocol: TCP
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "openshift"
        - name: OPENFEATURE_FLAGD_HOST
          value: "localhost"  # Sidecar는 localhost
        - name: OPENFEATURE_FLAGD_PORT
          value: "8013"
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
      # flagd Sidecar는 Operator가 자동 주입
```

### 4.3 Service

`openshift/02-service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: petclinic
  namespace: petclinic
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

### 4.4 Route

`openshift/03-route.yaml`:

```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: petclinic
  namespace: petclinic
  labels:
    app: petclinic
spec:
  host: petclinic.apps.your-openshift-domain.com
  to:
    kind: Service
    name: petclinic
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  wildcardPolicy: None
```

### 4.5 리소스 배포

```bash
# 모든 리소스 배포
oc apply -f openshift/01-deployment.yaml
oc apply -f openshift/02-service.yaml
oc apply -f openshift/03-route.yaml

# 또는 한 번에
oc apply -f openshift/

# 배포 상태 확인
oc get all -n petclinic

# Pod 상태 확인
oc get pods -n petclinic

# 예상 출력 (Sidecar 주입 전):
# NAME                                READY   STATUS    RESTARTS   AGE
# spring-petclinic-xxxxx-xxxxx        1/1     Running   0          2m
```

---

## 5. Feature Flag 정의

### 5.1 FeatureFlagSource

`openshift/04-featureflagsource.yaml`:

```yaml
apiVersion: core.openfeature.dev/v1beta1
kind: FeatureFlagSource
metadata:
  name: petclinic-flags
  namespace: petclinic
spec:
  sources:
  - source: petclinic/welcome-flags
    provider: kubernetes
```

### 5.2 FeatureFlag - 50/50 A/B Test

`openshift/05-featureflag.yaml`:

```yaml
apiVersion: core.openfeature.dev/v1beta1
kind: FeatureFlag
metadata:
  name: welcome-flags
  namespace: petclinic
  labels:
    app: petclinic
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
          # 50/50 랜덤 분할 (사용자 ID 기반)
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

### 5.3 Feature Flag 배포

```bash
# FeatureFlagSource 배포
oc apply -f openshift/04-featureflagsource.yaml

# FeatureFlag 배포
oc apply -f openshift/05-featureflag.yaml

# 배포 확인
oc get featureflagsource -n petclinic
oc get featureflag -n petclinic

# 상세 정보
oc describe featureflag welcome-flags -n petclinic
```

### 5.4 Sidecar 주입 확인

```bash
# Pod 재시작 (Sidecar 주입)
oc rollout restart deployment/spring-petclinic -n petclinic

# Pod 상태 확인
oc get pods -n petclinic

# 예상 출력 (Sidecar 주입 후):
# NAME                                READY   STATUS    RESTARTS   AGE
# spring-petclinic-xxxxx-xxxxx        2/2     Running   0          1m
#                                     ↑
#                     Spring Boot + flagd Sidecar

# Pod 상세 정보
oc describe pod -n petclinic -l app=petclinic

# 출력에서 확인:
# Containers:
#   petclinic:
#     Port: 8080/TCP
#   flagd:
#     Port: 8013/TCP (gRPC)
#     Port: 8014/TCP (HTTP)
```

---

## 6. 배포 검증

### 6.1 Pod 로그 확인

```bash
# 애플리케이션 로그
oc logs -n petclinic -l app=petclinic -c petclinic --tail=100

# 예상 로그:
# ✅ OpenFeature initialized successfully
#    Provider: flagd
#    Host: localhost
#    Port: 8013

# flagd Sidecar 로그
oc logs -n petclinic -l app=petclinic -c flagd --tail=100

# 예상 로그:
# flagd started successfully
# Loaded feature flags: welcome-page-redesign
```

### 6.2 애플리케이션 접근

```bash
# Route 주소 확인
oc get route petclinic -n petclinic -o jsonpath='{.spec.host}'

# 출력 예시:
# petclinic.apps.your-openshift-domain.com

# 브라우저에서 접속
open https://petclinic.apps.your-openshift-domain.com
```

### 6.3 Health Check

```bash
# Actuator Health Endpoint
ROUTE=$(oc get route petclinic -n petclinic -o jsonpath='{.spec.host}')
curl https://$ROUTE/actuator/health

# 예상 출력:
# {"status":"UP"}

# Readiness Probe
curl https://$ROUTE/actuator/health/readiness

# Liveness Probe
curl https://$ROUTE/actuator/health/liveness
```

### 6.4 flagd Metrics

```bash
# flagd Pod 이름 가져오기
POD_NAME=$(oc get pods -n petclinic -l app=petclinic -o jsonpath='{.items[0].metadata.name}')

# flagd 메트릭 조회
oc exec -n petclinic $POD_NAME -c flagd -- \
  curl -s http://localhost:8014/metrics | grep openfeature

# 출력 예시:
# openfeature_flag_evaluations_total{flag="welcome-page-redesign",variant="on"} 523
# openfeature_flag_evaluations_total{flag="welcome-page-redesign",variant="off"} 477
```

---

## 7. A/B 테스트 실행

### 7.1 점진적 롤아웃 전략

#### Week 1: 10% B 버전

```bash
# FeatureFlag 수정
oc edit featureflag welcome-flags -n petclinic
```

YAML 편집:
```yaml
targeting:
  if:
    - fractional:
      - var: targetingKey
      - - on
        - 10    # 10% → B
      - - off
        - 90    # 90% → A
    - on
    - off
```

저장하면 **즉시 반영** (애플리케이션 재배포 불필요!)

#### Week 2: 50% B 버전

```bash
oc edit featureflag welcome-flags -n petclinic
```

```yaml
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

#### Week 3: 100% B 버전 (성공 시)

```bash
oc edit featureflag welcome-flags -n petclinic
```

```yaml
defaultVariant: on
targeting: {}  # Targeting 제거
```

또는 kubectl patch:

```bash
oc patch featureflag welcome-flags -n petclinic \
  --type=merge \
  -p '{"spec":{"flagSpec":{"flags":{"welcome-page-redesign":{"defaultVariant":"on","targeting":{}}}}}}'
```

### 7.2 타게팅 전략 (고급)

#### 내부 직원 먼저 테스트

```yaml
targeting:
  if:
    - ends_with:
      - var: email
      - "@yourcompany.com"
    - on
    - if:
      - fractional:
        - var: targetingKey
        - - on
          - 10
        - - off
          - 90
      - on
      - off
```

#### 지역별 롤아웃

```yaml
targeting:
  if:
    - in:
      - var: country
      - - "KR"
        - "JP"
        - "US"
    - on
    - off
```

---

## 8. 모니터링

### 8.1 Prometheus 메트릭 수집

#### 8.1.1 ServiceMonitor 생성

`openshift/06-servicemonitor.yaml`:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: petclinic
  namespace: petclinic
  labels:
    app: petclinic
spec:
  selector:
    matchLabels:
      app: petclinic
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

```bash
# ServiceMonitor 배포
oc apply -f openshift/06-servicemonitor.yaml
```

#### 8.1.2 주요 메트릭

```promql
# Variant A 요청 수
sum(rate(welcome_variant_total{version="A"}[5m]))

# Variant B 요청 수
sum(rate(welcome_variant_total{version="B"}[5m]))

# B 버전 비율
sum(rate(welcome_variant_total{version="B"}[5m]))
/
sum(rate(welcome_variant_total[5m])) * 100

# flagd 평가 횟수
openfeature_flag_evaluations_total{flag="welcome-page-redesign"}

# flagd 평가 레이턴시
histogram_quantile(0.95,
  rate(openfeature_flag_evaluation_duration_seconds_bucket[5m])
)
```

### 8.2 Grafana 대시보드

OpenShift 내장 Grafana 또는 별도 Grafana 사용:

```bash
# Grafana Route 확인 (OpenShift Monitoring)
oc get route grafana -n openshift-monitoring
```

**대시보드 패널**:
1. **Traffic Split**: Variant A vs B 비율 (Pie Chart)
2. **Request Rate**: 시간별 요청 수 (Line Graph)
3. **Error Rate**: Variant별 에러율 (Line Graph)
4. **Response Time**: Variant별 응답 시간 (Line Graph)

### 8.3 로그 집계

```bash
# 최근 100개 로그
oc logs -n petclinic -l app=petclinic -c petclinic --tail=100

# 특정 사용자 추적
oc logs -n petclinic -l app=petclinic -c petclinic | grep "User abc123"

# Variant B 로그만 필터링
oc logs -n petclinic -l app=petclinic -c petclinic | grep "Variant B"
```

---

## 9. 롤백 및 문제 해결

### 9.1 긴급 롤백 (즉시)

#### 방법 1: Flag Disable

```bash
oc patch featureflag welcome-flags -n petclinic \
  --type=merge \
  -p '{"spec":{"flagSpec":{"flags":{"welcome-page-redesign":{"state":"DISABLED"}}}}}'
```

#### 방법 2: DefaultVariant 변경

```bash
oc patch featureflag welcome-flags -n petclinic \
  --type=merge \
  -p '{"spec":{"flagSpec":{"flags":{"welcome-page-redesign":{"defaultVariant":"off"}}}}}'
```

#### 방법 3: 100% A로 전환

```bash
oc edit featureflag welcome-flags -n petclinic
```

```yaml
targeting:
  if:
    - fractional:
      - var: targetingKey
      - - on
        - 0     # 0% → B
      - - off
        - 100   # 100% → A
    - on
    - off
```

### 9.2 일반적인 문제

#### 문제 1: Sidecar 주입 안 됨

**증상**: Pod에 flagd 컨테이너가 없음 (READY 1/1)

**해결**:
```bash
# 어노테이션 확인
oc get deployment spring-petclinic -n petclinic -o yaml | grep openfeature

# 어노테이션 추가
oc patch deployment spring-petclinic -n petclinic \
  --type=merge \
  -p '{"spec":{"template":{"metadata":{"annotations":{"openfeature.dev/enabled":"true","openfeature.dev/featureflagsource":"petclinic-flags"}}}}}'

# Operator Pod 확인
oc get pods -n openfeature-operator-system

# Operator 로그 확인
oc logs -n openfeature-operator-system -l control-plane=controller-manager
```

#### 문제 2: flagd 연결 실패

**증상**: `Connection refused: localhost:8013`

**해결**:
```bash
# flagd Pod 로그 확인
oc logs -n petclinic -l app=petclinic -c flagd

# flagd 포트 확인
oc get pods -n petclinic -o yaml | grep -A 5 "containerPort"

# 환경 변수 확인
oc set env deployment/spring-petclinic --list -n petclinic | grep FLAGD
```

#### 문제 3: Flag 값이 항상 기본값

**증상**: 모든 사용자가 A 버전만 봄

**해결**:
```bash
# FeatureFlag 확인
oc get featureflag welcome-flags -n petclinic -o yaml

# Flag 상태 확인
oc describe featureflag welcome-flags -n petclinic

# flagd에서 Flag 조회
POD_NAME=$(oc get pods -n petclinic -l app=petclinic -o jsonpath='{.items[0].metadata.name}')
oc exec -n petclinic $POD_NAME -c flagd -- \
  curl -s http://localhost:8014/flags/welcome-page-redesign
```

#### 문제 4: 이미지 Pull 실패

**증상**: `ImagePullBackOff` 상태

**해결**:
```bash
# Secret 확인
oc get secrets -n petclinic | grep quay

# Secret이 없으면 생성
oc create secret docker-registry quay-secret \
  --docker-server=quay.io \
  --docker-username=your-username \
  --docker-password=your-password \
  -n petclinic

# Deployment에 imagePullSecrets 추가
oc patch deployment spring-petclinic -n petclinic \
  --type=merge \
  -p '{"spec":{"template":{"spec":{"imagePullSecrets":[{"name":"quay-secret"}]}}}}'
```

### 9.3 디버깅 명령어

```bash
# Pod 상세 정보
oc describe pod -n petclinic -l app=petclinic

# 이벤트 확인
oc get events -n petclinic --sort-by='.lastTimestamp'

# 리소스 사용량
oc top pods -n petclinic

# Pod 내부 접속 (디버깅)
oc exec -it -n petclinic $POD_NAME -c petclinic -- /bin/sh

# flagd 상태 확인 (Pod 내부)
curl http://localhost:8014/healthz
curl http://localhost:8014/flags
```

---

## 10. 정리 (Clean Up)

### 10.1 리소스 삭제

```bash
# Feature Flags 삭제
oc delete featureflag welcome-flags -n petclinic
oc delete featureflagsource petclinic-flags -n petclinic

# 애플리케이션 삭제
oc delete deployment spring-petclinic -n petclinic
oc delete service petclinic -n petclinic
oc delete route petclinic -n petclinic

# 또는 한 번에
oc delete -f openshift/

# Namespace 삭제
oc delete project petclinic
```

### 10.2 OpenFeature Operator 제거

```bash
# Helm으로 제거
helm uninstall openfeature-operator -n openfeature-operator-system

# Namespace 삭제
oc delete namespace openfeature-operator-system

# CRD 삭제 (선택)
oc delete crd featureflags.core.openfeature.dev
oc delete crd featureflagsources.core.openfeature.dev
```

---

## 11. 체크리스트

### 배포 전 체크리스트

- [ ] OpenShift 클러스터 접근 확인
- [ ] oc CLI 설치 및 로그인
- [ ] Namespace 생성
- [ ] 이미지 레지스트리 설정
- [ ] OpenFeature Operator 설치
- [ ] CRD 확인

### 배포 체크리스트

- [ ] 애플리케이션 이미지 빌드 & 푸시
- [ ] Deployment 배포
- [ ] Service 배포
- [ ] Route 배포
- [ ] FeatureFlagSource 배포
- [ ] FeatureFlag 배포 (10% 시작)
- [ ] Pod 상태 확인 (2/2 Running)
- [ ] Sidecar 주입 확인

### 검증 체크리스트

- [ ] 애플리케이션 로그 확인
- [ ] flagd 로그 확인
- [ ] Route 접속 테스트
- [ ] Health Check 확인
- [ ] A/B 버전 모두 동작 확인

### A/B 테스트 체크리스트

- [ ] Week 1: 10% B 트래픽 설정
- [ ] 에러율 모니터링 (3일)
- [ ] Week 2: 50% B 트래픽 확대
- [ ] 메트릭 수집 및 분석
- [ ] Week 3: 의사결정 (100% or 롤백)

---

## 12. 참고 자료

- [OpenShift Documentation](https://docs.openshift.com/)
- [OpenFeature Operator Quick Start](https://openfeature.dev/docs/tutorials/open-feature-operator/quick-start/)
- [flagd Kubernetes Deployment](https://flagd.dev/deployment/kubernetes/)
- [OpenShift Routes](https://docs.openshift.com/container-platform/latest/networking/routes/route-configuration.html)

---

## 다음 단계

1. ✅ **배포 완료** (현재 문서)
2. 🚀 **A/B 테스트 실행**
3. 📊 **메트릭 분석 및 최적화**
4. 🎓 **팀 교육 및 문서화**
