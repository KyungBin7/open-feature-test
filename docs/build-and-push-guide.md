# Spring PetClinic 이미지 빌드 및 푸시 가이드

> **작성일**: 2025-10-01
> **환경**: OpenShift (Kyobo MTP)
> **레지스트리**: default-route-openshift-image-registry.apps.kyobo.mtp.local

## 📑 목차

- [1. 사전 준비](#1-사전-준비)
- [2. Dockerfile 생성](#2-dockerfile-생성)
- [3. 이미지 빌드](#3-이미지-빌드)
- [4. 이미지 태그](#4-이미지-태그)
- [5. 레지스트리 로그인](#5-레지스트리-로그인)
- [6. 이미지 푸시](#6-이미지-푸시)
- [7. 이미지 확인](#7-이미지-확인)

---

## 1. 사전 준비

### 1.1 필수 도구 확인

```bash
# oc CLI 설치 확인
oc version

# Podman 설치 확인
podman --version
```

### 1.2 OpenShift 로그인

```bash
# OpenShift 클러스터 로그인
oc login --server=https://api.kyobo.mtp.local:6443

# 현재 사용자 확인
oc whoami
```

### 1.3 프로젝트 생성 또는 선택

```bash
# 새 프로젝트 생성
oc new-project petclinic

# 또는 기존 프로젝트 선택
oc project petclinic

# 현재 프로젝트 확인
oc project -q
```

---

## 2. Dockerfile 생성

프로젝트 루트에 `Dockerfile` 생성:

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

### Dockerfile 특징

- **Red Hat UBI 이미지 사용**: Docker Hub Rate Limit 회피
- **Multi-stage Build**: 빌드와 런타임 환경 분리로 이미지 크기 최적화
- **필수 패키지 설치**: gzip, tar (Maven wrapper 동작에 필요)
- **테스트 스킵**: 빌드 시간 단축을 위해 `-DskipTests` 사용

---

## 3. 이미지 빌드

### 3.1 빌드 실행

```bash
# 이미지 빌드
podman build -t spring-petclinic:latest .
```

### 3.2 빌드 진행 상황

```
[1/2] STEP 1/9: FROM registry.access.redhat.com/ubi8/openjdk-17:1.20 AS builder
[1/2] STEP 2/9: USER root
[1/2] STEP 3/9: WORKDIR /app
[1/2] STEP 4/9: RUN microdnf install -y gzip tar && microdnf clean all
[1/2] STEP 5/9: COPY .mvn/ .mvn
[1/2] STEP 6/9: COPY mvnw pom.xml ./
[1/2] STEP 7/9: RUN chmod +x ./mvnw
[1/2] STEP 8/9: RUN ./mvnw dependency:go-offline
[1/2] STEP 9/9: COPY src ./src
[1/2] STEP 10/10: RUN ./mvnw clean package -DskipTests
[2/2] STEP 1/4: FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.20
[2/2] STEP 2/4: WORKDIR /app
[2/2] STEP 3/4: COPY --from=builder /app/target/*.jar app.jar
[2/2] STEP 4/4: EXPOSE 8080
[2/2] STEP 5/5: ENTRYPOINT ["java", "-jar", "app.jar"]
COMMIT spring-petclinic:latest
```

**빌드 소요 시간**: 약 5-10분 (첫 빌드 시)

### 3.3 빌드 결과 확인

```bash
# 이미지 확인
podman images | grep spring-petclinic

# 예상 출력:
# localhost/spring-petclinic    latest    abc123def456   2 minutes ago   280 MB
```

---

## 4. 이미지 태그

### 4.1 환경 변수 설정

```bash
# 현재 프로젝트 이름
PROJECT=$(oc project -q)

# 레지스트리 주소
REGISTRY="default-route-openshift-image-registry.apps.kyobo.mtp.local"

# 이미지 이름
IMAGE_NAME="spring-petclinic"

# 변수 확인
echo "프로젝트: $PROJECT"
echo "레지스트리: $REGISTRY"
echo "이미지: $IMAGE_NAME"
```

### 4.2 이미지 태그

```bash
# latest 태그
podman tag spring-petclinic:latest \
  $REGISTRY/$PROJECT/$IMAGE_NAME:latest

# 버전 태그 (v1.0.0)
podman tag spring-petclinic:latest \
  $REGISTRY/$PROJECT/$IMAGE_NAME:v1.0.0

# 태그 확인
podman images | grep spring-petclinic
```

**예상 출력**:
```
localhost/spring-petclinic                                                          latest    abc123def456   5 minutes ago   280 MB
default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic   latest    abc123def456   5 minutes ago   280 MB
default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic   v1.0.0    abc123def456   5 minutes ago   280 MB
```

---

## 5. 레지스트리 로그인

### 5.1 OpenShift 토큰 가져오기

```bash
# 토큰 조회
TOKEN=$(oc whoami -t)
echo $TOKEN
```

### 5.2 Podman 로그인

```bash
# 레지스트리 로그인
echo $TOKEN | podman login -u $(oc whoami) --password-stdin \
  --tls-verify=false \
  default-route-openshift-image-registry.apps.kyobo.mtp.local
```

**성공 메시지**:
```
Login Succeeded!
```

### 로그인 참고 사항

- `--tls-verify=false`: 자체 서명된 인증서 사용 시 필요
- OpenShift 내부 레지스트리는 클러스터 내부에서만 접근 가능
- 토큰은 24시간 유효 (만료 시 재로그인 필요)

---

## 6. 이미지 푸시

### 6.1 latest 태그 푸시

```bash
# latest 태그 푸시
podman push --tls-verify=false \
  $REGISTRY/$PROJECT/$IMAGE_NAME:latest
```

**진행 상황**:
```
Getting image source signatures
Copying blob sha256:xxxxx
Copying blob sha256:yyyyy
Copying blob sha256:zzzzz
Copying config sha256:aaaaa
Writing manifest to image destination
Storing signatures
```

### 6.2 버전 태그 푸시

```bash
# v1.0.0 태그 푸시
podman push --tls-verify=false \
  $REGISTRY/$PROJECT/$IMAGE_NAME:v1.0.0
```

### 푸시 소요 시간

- **네트워크 속도에 따라 다름**: 약 2-5분
- **이미지 크기**: ~280MB

---

## 7. 이미지 확인

### 7.1 ImageStream 확인

```bash
# ImageStream 목록
oc get imagestream -n $PROJECT

# 예상 출력:
# NAME                DOCKER REPO                                                                          TAGS           UPDATED
# spring-petclinic    default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic   latest,v1.0.0   2 minutes ago
```

### 7.2 ImageStream 상세 정보

```bash
# ImageStream 상세 정보
oc describe imagestream spring-petclinic -n $PROJECT
```

**출력 예시**:
```
Name:                   spring-petclinic
Namespace:              petclinic
Created:                2 minutes ago
Labels:                 <none>
Annotations:            <none>
Image Repository:       default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic
Image Lookup:           local=false
Unique Images:          1
Tags:                   2

latest
  tagged from default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic:latest
  * default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic@sha256:xxxxx
      2 minutes ago

v1.0.0
  tagged from default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic:v1.0.0
  * default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic@sha256:xxxxx
      1 minute ago
```

### 7.3 이미지 SHA 확인

```bash
# 이미지 SHA 조회
oc get imagestreamtag spring-petclinic:latest -n $PROJECT \
  -o jsonpath='{.image.dockerImageReference}'

# 출력:
# default-route-openshift-image-registry.apps.kyobo.mtp.local/petclinic/spring-petclinic@sha256:xxxxx
```

---

## 🚀 전체 프로세스 자동화 스크립트

```bash
#!/bin/bash

# 변수 설정
PROJECT=$(oc project -q)
REGISTRY="default-route-openshift-image-registry.apps.kyobo.mtp.local"
IMAGE_NAME="spring-petclinic"
TOKEN=$(oc whoami -t)

echo "======================================"
echo "Spring PetClinic 이미지 빌드 & 푸시"
echo "======================================"
echo "프로젝트: $PROJECT"
echo "레지스트리: $REGISTRY"
echo "이미지: $IMAGE_NAME"
echo "======================================"

# 1. 이미지 빌드
echo "✓ Step 1: 이미지 빌드 중..."
podman build -t spring-petclinic:latest .
if [ $? -ne 0 ]; then
  echo "❌ 이미지 빌드 실패"
  exit 1
fi

# 2. 이미지 태그
echo "✓ Step 2: 이미지 태그 중..."
podman tag spring-petclinic:latest $REGISTRY/$PROJECT/$IMAGE_NAME:latest
podman tag spring-petclinic:latest $REGISTRY/$PROJECT/$IMAGE_NAME:v1.0.0

# 3. 레지스트리 로그인
echo "✓ Step 3: 레지스트리 로그인 중..."
echo $TOKEN | podman login -u $(oc whoami) --password-stdin \
  --tls-verify=false $REGISTRY
if [ $? -ne 0 ]; then
  echo "❌ 레지스트리 로그인 실패"
  exit 1
fi

# 4. 이미지 푸시
echo "✓ Step 4: 이미지 푸시 중 (latest)..."
podman push --tls-verify=false $REGISTRY/$PROJECT/$IMAGE_NAME:latest
if [ $? -ne 0 ]; then
  echo "❌ 이미지 푸시 실패 (latest)"
  exit 1
fi

echo "✓ Step 5: 이미지 푸시 중 (v1.0.0)..."
podman push --tls-verify=false $REGISTRY/$PROJECT/$IMAGE_NAME:v1.0.0
if [ $? -ne 0 ]; then
  echo "❌ 이미지 푸시 실패 (v1.0.0)"
  exit 1
fi

# 5. 확인
echo "✓ Step 6: ImageStream 확인..."
oc get imagestream spring-petclinic -n $PROJECT

echo "======================================"
echo "✅ 이미지 빌드 및 푸시 완료!"
echo "======================================"
echo ""
echo "다음 단계:"
echo "1. Deployment 생성"
echo "2. Service 생성"
echo "3. Route 생성"
echo "4. 애플리케이션 동작 확인"
```

**스크립트 사용 방법**:

```bash
# 스크립트 저장
cat > build-and-push.sh << 'EOF'
[위 스크립트 내용 복사]
EOF

# 실행 권한 부여
chmod +x build-and-push.sh

# 실행
./build-and-push.sh
```

---

## 📋 체크리스트

빌드 및 푸시 전체 과정:

- [x] OpenShift 클러스터 로그인
- [x] 프로젝트 생성/선택 (petclinic)
- [x] Dockerfile 생성 (Red Hat UBI 기반)
- [x] 이미지 빌드 (podman build)
- [x] 이미지 태그 (latest, v1.0.0)
- [x] OpenShift 레지스트리 로그인
- [x] 이미지 푸시 (latest)
- [x] 이미지 푸시 (v1.0.0)
- [x] ImageStream 확인

---

## ⚠️ 문제 해결

### 1. Maven 의존성 다운로드 실패

**증상**: `tar (child): gzip: Cannot exec: No such file or directory`

**해결**: Dockerfile에 gzip, tar 패키지 설치 추가
```dockerfile
RUN microdnf install -y gzip tar && microdnf clean all
```

### 2. 권한 오류

**증상**: `chmod: changing permissions of './mvnw': Operation not permitted`

**해결**: Dockerfile에서 USER root 명령 추가
```dockerfile
USER root
```

### 3. Docker Hub Rate Limit

**증상**: `toomanyrequests: You have reached your unauthenticated pull rate limit`

**해결**: Red Hat UBI 이미지 사용
```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17:1.20
```

### 4. TLS 인증서 오류

**증상**: `x509: certificate signed by unknown authority`

**해결**: `--tls-verify=false` 옵션 사용
```bash
podman login --tls-verify=false ...
podman push --tls-verify=false ...
```

---

## 📊 빌드 시간 및 리소스

### 빌드 소요 시간

- **첫 빌드**: 5-10분
  - 베이스 이미지 다운로드: 1-2분
  - Maven 의존성 다운로드: 3-5분
  - 애플리케이션 빌드: 2-3분
- **재빌드** (코드 변경 시): 2-3분
  - 레이어 캐싱으로 의존성 다운로드 스킵

### 이미지 크기

- **Builder Stage**: ~400MB
- **Final Image**: ~280MB

### 푸시 소요 시간

- **네트워크 속도에 따라 다름**: 2-5분

---

## 🎯 다음 단계

이미지가 성공적으로 푸시되었습니다. 이제 다음 단계로 진행할 수 있습니다:

1. **Deployment 생성**: 애플리케이션 배포
2. **Service 생성**: 내부 네트워크 노출
3. **Route 생성**: 외부 접근 가능하도록 설정
4. **동작 확인**: 애플리케이션 접속 테스트

---

## 참고 자료

- [Red Hat UBI Images](https://catalog.redhat.com/software/containers/ubi8/openjdk-17/618bdbf34ae3739687568813)
- [Podman Documentation](https://docs.podman.io/)
- [OpenShift Image Registry](https://docs.openshift.com/container-platform/latest/registry/index.html)
- [Spring Boot Container Images](https://spring.io/guides/topicals/spring-boot-docker/)
