# Spring PetClinic 배포 성공 가이드

> **작성일**: 2025-10-01
> **환경**: OpenShift (Kyobo MTP)
> **Namespace**: open-feature-test
> **상태**: ✅ 배포 성공

## 📑 목차

- [배포 개요](#배포-개요)
- [이미지 빌드 및 푸시](#이미지-빌드-및-푸시)
- [Kubernetes 리소스 배포](#kubernetes-리소스-배포)
- [애플리케이션 접근](#애플리케이션-접근)
- [주요 설정 사항](#주요-설정-사항)
- [문제 해결 경험](#문제-해결-경험)

---

## 배포 개요

### 환경 정보

- **OpenShift 클러스터**: Kyobo MTP
- **Namespace**: `open-feature-test`
- **레지스트리**: `default-route-openshift-image-registry.apps.kyobo.mtp.local`
- **이미지 태그**: `v1.0.0`

### 배포 아키텍처

```
┌─────────────────────────────────────────────────────┐
│  OpenShift Cluster (Kyobo MTP)                      │
│                                                     │
│  ┌───────────────────────────────────────────────┐ │
│  │  Namespace: open-feature-test                 │ │
│  │                                               │ │
│  │  ┌─────────────────────────────────────────┐ │ │
│  │  │  Deployment: spring-petclinic           │ │ │
│  │  │  - Replicas: 2                          │ │ │
│  │  │  - Image: v1.0.0                        │ │ │
│  │  │  ┌─────────┐    ┌─────────┐            │ │ │
│  │  │  │  Pod 1  │    │  Pod 2  │            │ │ │
│  │  │  │  :8080  │    │  :8080  │            │ │ │
│  │  │  └─────────┘    └─────────┘            │ │ │
│  │  └─────────────────────────────────────────┘ │ │
│  │                     ▲                         │ │
│  │  ┌──────────────────┴──────────────────────┐ │ │
│  │  │  Service: spring-petclinic              │ │ │
│  │  │  - ClusterIP: 172.30.x.x                │ │ │
│  │  │  - Port: 8080                           │ │ │
│  │  └──────────────────┬──────────────────────┘ │ │
│  │                     ▼                         │ │
│  │  ┌─────────────────────────────────────────┐ │ │
│  │  │  Route: spring-petclinic                │ │ │
│  │  │  - TLS: edge termination                │ │ │
│  │  │  - URL: *.apps.kyobo.mtp.local          │ │ │
│  │  └─────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
                        ▲
                        │ HTTPS
                        │
                   [사용자 브라우저]
```

---

## 이미지 빌드 및 푸시

### 1. Dockerfile 생성

프로젝트 루트에 `Dockerfile` 작성:

```dockerfile
# Stage 1: Build
FROM registry.access.redhat.com/ubi8/openjdk-17:1.20 AS builder

# root로 전환하여 권한 작업 수행
USER root
WORKDIR /app

# 필요한 패키지 설치
RUN microdnf install -y gzip tar && microdnf clean all

# Maven wrapper와 pom.xml 복사
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Maven wrapper에 실행 권한 부여
RUN chmod +x ./mvnw

# 의존성 다운로드 (캐싱 최적화)
RUN ./mvnw dependency:go-offline

# 소스 코드 복사
COPY src ./src

# 애플리케이션 빌드
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.20
WORKDIR /app

# 빌드 결과물 복사
COPY --from=builder /app/target/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**핵심 포인트**:
- ✅ Red Hat UBI 이미지 사용 (Docker Hub Rate Limit 회피)
- ✅ Multi-stage Build (이미지 크기 최적화)
- ✅ gzip, tar 패키지 설치 (Maven wrapper 동작 필수)
- ✅ USER root 설정 (권한 문제 해결)

### 2. 이미지 빌드

```bash
# bastion 서버에서 실행
cd /path/to/spring-petclinic

# 이미지 빌드
podman build -t spring-petclinic:latest .
```

**빌드 시간**: 약 5-10분 (첫 빌드)

### 3. 이미지 태그

```bash
# 환경 변수 설정
PROJECT="open-feature-test"
REGISTRY="default-route-openshift-image-registry.apps.kyobo.mtp.local"
IMAGE_NAME="spring-petclinic"

# 이미지 태그
podman tag spring-petclinic:latest $REGISTRY/$PROJECT/$IMAGE_NAME:latest
podman tag spring-petclinic:latest $REGISTRY/$PROJECT/$IMAGE_NAME:v1.0.0
```

### 4. 레지스트리 로그인 및 푸시

```bash
# OpenShift 토큰으로 로그인
TOKEN=$(oc whoami -t)
echo $TOKEN | podman login -u $(oc whoami) --password-stdin --tls-verify=false $REGISTRY

# 이미지 푸시
podman push --tls-verify=false $REGISTRY/$PROJECT/$IMAGE_NAME:latest
podman push --tls-verify=false $REGISTRY/$PROJECT/$IMAGE_NAME:v1.0.0
```

### 5. ImageStream 확인

```bash
# ImageStream 확인
oc get imagestream spring-petclinic -n open-feature-test

# 예상 출력:
# NAME                IMAGE REPOSITORY                                                                          TAGS           UPDATED
# spring-petclinic    default-route-openshift-image-registry.apps.kyobo.mtp.local/open-feature-test/spring-petclinic   latest,v1.0.0   2 minutes ago
```

---

## Kubernetes 리소스 배포

### 1. Deployment 생성

**파일**: `docs/deployment.yaml`

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
  selector:
    matchLabels:
      app: petclinic
  template:
    metadata:
      labels:
        app: petclinic
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
```

**핵심 포인트**:
- ✅ **이미지 주소**: `image-registry.openshift-image-registry.svc:5000` (내부 서비스 주소 사용)
- ✅ **태그**: `v1.0.0` (명시적 버전 관리)
- ✅ **Replicas**: 2개 (고가용성)
- ✅ **Health Probes**: Liveness, Readiness 설정

**배포 명령어**:
```bash
oc apply -f docs/deployment.yaml
```

### 2. Service 생성

**파일**: `docs/service.yaml`

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

**배포 명령어**:
```bash
oc apply -f docs/service.yaml
```

### 3. Route 생성

**파일**: `docs/route.yaml`

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

**배포 명령어**:
```bash
oc apply -f docs/route.yaml
```

### 4. 배포 상태 확인

```bash
# 전체 리소스 확인
oc get all -n open-feature-test -l app=petclinic

# Pod 상태 확인
oc get pods -n open-feature-test -l app=petclinic

# 예상 출력:
# NAME                                READY   STATUS    RESTARTS   AGE
# spring-petclinic-xxxxx-xxxxx        1/1     Running   0          5m
# spring-petclinic-yyyyy-yyyyy        1/1     Running   0          5m
```

---

## 애플리케이션 접근

### 1. Route URL 확인

```bash
# Route URL 가져오기
oc get route spring-petclinic -n open-feature-test -o jsonpath='https://{.spec.host}'
echo ""

# 또는 전체 정보 확인
oc get route spring-petclinic -n open-feature-test
```

**예상 출력**:
```
https://spring-petclinic-open-feature-test.apps.kyobo.mtp.local
```

### 2. 로컬에서 브라우저 접속 (VPN 환경)

VPN으로 bastion 서버에 접속한 경우, 로컬 웹 브라우저에서 애플리케이션에 접근하려면 `/etc/hosts` 파일에 Route IP를 추가해야 합니다.

#### Step 1: OpenShift Router IP 확인

```bash
# bastion 서버에서 실행
# Router Pod가 실행 중인 Node의 IP 확인
oc get pods -n openshift-ingress -o wide | grep router

# 또는 Ingress Controller의 External IP 확인
oc get service -n openshift-ingress router-default

# 또는 Node IP 확인
oc get nodes -o wide
```

일반적으로 Router Pod가 실행 중인 Node의 IP를 사용합니다.

#### Step 2: 로컬 /etc/hosts 파일 편집

**macOS/Linux:**
```bash
sudo vi /etc/hosts

# 아래 라인 추가 (Router IP로 변경)
<ROUTER-IP>  spring-petclinic-open-feature-test.apps.kyobo.mtp.local
```

**예시:**
```
192.168.1.100  spring-petclinic-open-feature-test.apps.kyobo.mtp.local
```

#### Step 3: 브라우저 접속

브라우저에서 다음 URL 접속:
```
https://spring-petclinic-open-feature-test.apps.kyobo.mtp.local
```

**확인 사항**:
- ✅ Spring PetClinic 메인 페이지 표시
- ✅ "Welcome" 메시지 및 펫 이미지
- ✅ 상단 네비게이션 메뉴 (Home, Find Owners, Veterinarians)

> **참고**: VPN 환경에서 로컬 웹 브라우저로 접근하려면 `/etc/hosts` 파일에 Router IP와 도메인을 매핑해야 합니다. 이는 OpenShift의 내부 DNS가 VPN을 통해 로컬에서 직접 해석되지 않기 때문입니다.

### 3. Health Check

```bash
# Health Check
ROUTE_URL=$(oc get route spring-petclinic -n open-feature-test -o jsonpath='{.spec.host}')
curl -k https://$ROUTE_URL/actuator/health

# 예상 출력:
# {"status":"UP"}

# Liveness Probe
curl -k https://$ROUTE_URL/actuator/health/liveness

# Readiness Probe
curl -k https://$ROUTE_URL/actuator/health/readiness
```

### 4. 애플리케이션 로그 확인

```bash
# 실시간 로그
oc logs -f -n open-feature-test -l app=petclinic

# 최근 100줄
oc logs -n open-feature-test -l app=petclinic --tail=100
```

---

## 주요 설정 사항

### 1. 리소스 할당

| 항목 | Request | Limit |
|------|---------|-------|
| **Memory** | 512Mi | 1Gi |
| **CPU** | 500m | 1000m |

### 2. Health Check 설정

| Probe | Path | 초기 대기 | 주기 |
|-------|------|----------|------|
| **Liveness** | `/actuator/health/liveness` | 60초 | 10초 |
| **Readiness** | `/actuator/health/readiness` | 30초 | 5초 |

### 3. 이미지 정보

- **Base Image**: Red Hat UBI 8 + OpenJDK 17
- **최종 이미지 크기**: ~280MB
- **태그**: `v1.0.0`

### 4. 네트워크 설정

- **Service Type**: ClusterIP
- **Port**: 8080
- **TLS**: Edge Termination
- **HTTP → HTTPS**: 자동 리다이렉트

---

## 문제 해결 경험

### 문제 1: Maven wrapper 권한 오류

**증상**:
```
chmod: changing permissions of './mvnw': Operation not permitted
```

**해결**:
```dockerfile
# Dockerfile에 USER root 추가
USER root
WORKDIR /app
```

### 문제 2: gzip/tar 패키지 누락

**증상**:
```
tar (child): gzip: Cannot exec: No such file or directory
```

**해결**:
```dockerfile
# 필요한 패키지 설치
RUN microdnf install -y gzip tar && microdnf clean all
```

### 문제 3: Docker Hub Rate Limit

**증상**:
```
toomanyrequests: You have reached your unauthenticated pull rate limit
```

**해결**:
```dockerfile
# Red Hat UBI 이미지 사용
FROM registry.access.redhat.com/ubi8/openjdk-17:1.20
```

### 문제 4: ImagePullBackOff (TLS 인증서 오류)

**증상**:
```
Failed to pull image: x509: certificate signed by unknown authority
```

**원인**:
- 외부 Route 주소 사용 시 자체 서명 인증서 신뢰 문제

**해결**:
```yaml
# Deployment에서 내부 서비스 주소 사용
image: image-registry.openshift-image-registry.svc:5000/open-feature-test/spring-petclinic:v1.0.0
```

**두 가지 레지스트리 주소**:
- **외부 (Push 용)**: `default-route-openshift-image-registry.apps.kyobo.mtp.local` (Podman/Docker)
- **내부 (Pull 용)**: `image-registry.openshift-image-registry.svc:5000` (Deployment)

---

## 배포 체크리스트

### 이미지 빌드 & 푸시
- [x] Dockerfile 작성
- [x] 이미지 빌드 (podman build)
- [x] 이미지 태그 (latest, v1.0.0)
- [x] 레지스트리 로그인
- [x] 이미지 푸시
- [x] ImageStream 확인

### Kubernetes 리소스 배포
- [x] Namespace 확인 (open-feature-test)
- [x] Deployment 생성
- [x] Service 생성
- [x] Route 생성
- [x] Pod Running 확인 (2/2)
- [x] Service Endpoint 확인
- [x] Route URL 확인

### 애플리케이션 확인
- [x] Health Check 성공
- [x] 웹 브라우저 접속 확인
- [x] 메인 페이지 정상 표시
- [x] 네비게이션 동작 확인

---

## 배포 요약

### 성공 포인트

1. ✅ **Red Hat UBI 이미지 사용**: Docker Hub Rate Limit 회피
2. ✅ **Multi-stage Build**: 이미지 크기 최적화 (280MB)
3. ✅ **내부 레지스트리 주소**: TLS 인증서 문제 해결
4. ✅ **명시적 버전 태그**: v1.0.0 사용
5. ✅ **Health Probes 설정**: 안정적인 배포
6. ✅ **2개 Replica**: 고가용성 확보

### 배포 시간

- **이미지 빌드**: 5-10분
- **이미지 푸시**: 2-5분
- **Kubernetes 리소스 배포**: 1-2분
- **Pod 시작 대기**: 1-2분
- **총 소요 시간**: 약 10-20분

### 최종 상태

```
✅ Deployment: spring-petclinic (2/2 Running)
✅ Service: spring-petclinic (ClusterIP)
✅ Route: spring-petclinic-open-feature-test.apps.kyobo.mtp.local
✅ 애플리케이션: 정상 동작 확인
```

---

## 다음 단계

1. **모니터링 설정**: Prometheus, Grafana 연동
2. **로그 수집**: EFK/ELK 스택 연동
3. **Auto-scaling**: HPA (Horizontal Pod Autoscaler) 설정
4. **OpenFeature 적용**: A/B 테스트 기능 구현
5. **CI/CD 파이프라인**: Jenkins/Tekton 연동

---

## 참고 자료

### 문서 위치
- `docs/Dockerfile` - 이미지 빌드 정의
- `docs/deployment.yaml` - Deployment 리소스
- `docs/service.yaml` - Service 리소스
- `docs/route.yaml` - Route 리소스
- `docs/build-and-push-guide.md` - 이미지 빌드 가이드
- `docs/deployment-commands.md` - 배포 명령어 가이드

### OpenShift 리소스
- **Namespace**: open-feature-test
- **Deployment**: spring-petclinic
- **Service**: spring-petclinic
- **Route**: spring-petclinic
- **ImageStream**: spring-petclinic

### 주요 명령어
```bash
# 상태 확인
oc get all -n open-feature-test -l app=petclinic

# 로그 확인
oc logs -f -n open-feature-test -l app=petclinic

# Route URL 확인
oc get route spring-petclinic -n open-feature-test

# Pod 접속
oc exec -it -n open-feature-test $(oc get pods -n open-feature-test -l app=petclinic -o jsonpath='{.items[0].metadata.name}') -- /bin/bash
```
