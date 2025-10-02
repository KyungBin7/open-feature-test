# OpenFeature Manifests for OpenShift

OpenShift에 OpenFeature를 사용하는 Spring PetClinic 애플리케이션을 배포하기 위한 Kubernetes/OpenShift 매니페스트 파일들입니다.

## 📂 파일 구조

```
manifests/
├── 01-featureflagsource.yaml  # FeatureFlagSource CR
├── 02-featureflag.yaml        # FeatureFlag CR (50/50 A/B)
├── 03-deployment.yaml         # 수정된 Deployment (Sidecar 주입)
├── 04-service.yaml            # Service
├── 05-route.yaml              # Route (OpenShift)
└── README.md                  # 이 파일
```

## 🚀 배포 순서

### 사전 준비

1. **OpenFeature Operator 설치 확인**
```bash
oc get pods -n openfeature-operator-system
```

2. **이미지 빌드 및 푸시**
```bash
# 빌드
./mvnw clean package -DskipTests

# OpenShift BuildConfig 또는 로컬 빌드
oc start-build spring-petclinic --from-dir=. --follow
```

### 배포 실행

#### 1. FeatureFlagSource 배포
```bash
oc apply -f 01-featureflagsource.yaml

# 확인
oc get featureflagsource -n open-feature-test
```

#### 2. FeatureFlag CR 배포 (50/50 A/B)
```bash
oc apply -f 02-featureflag.yaml

# 확인
oc get featureflag -n open-feature-test
oc describe featureflag welcome-flags -n open-feature-test
```

#### 3. Deployment 배포
```bash
oc apply -f 03-deployment.yaml

# Rolling Update 확인
oc rollout status deployment/spring-petclinic -n open-feature-test

# Pod 상태 확인 (2/2 READY 확인)
oc get pods -n open-feature-test
```

#### 4. Service & Route 배포
```bash
oc apply -f 04-service.yaml
oc apply -f 05-route.yaml

# Route 주소 확인
oc get route spring-petclinic -n open-feature-test
```

### 또는 한 번에 배포
```bash
oc apply -f .
```

## 🔍 검증

### Pod 확인
```bash
# Pod 목록 (2/2 READY 확인)
oc get pods -n open-feature-test

# Pod 상세 정보 (flagd Sidecar 확인)
oc describe pod -n open-feature-test -l app=petclinic
```

### 로그 확인
```bash
# Spring Boot 로그
oc logs -n open-feature-test -l app=petclinic -c petclinic --tail=50

# flagd 로그
oc logs -n open-feature-test -l app=petclinic -c flagd --tail=50
```

### 애플리케이션 접속
```bash
# Route 주소 확인
ROUTE=$(oc get route spring-petclinic -n open-feature-test -o jsonpath='{.spec.host}')
echo "https://$ROUTE"

# Health Check
curl https://$ROUTE/actuator/health

# 브라우저에서 접속
open https://$ROUTE
```

## 🎯 Feature Flag 관리

### 현재 Flag 상태 확인
```bash
oc get featureflag welcome-flags -n open-feature-test -o yaml
```

### Flag 비율 변경 (50% → 100% B)
```bash
oc patch featureflag welcome-flags -n open-feature-test \
  --type=merge \
  -p '{"spec":{"flagSpec":{"flags":{"welcome-page-redesign":{"defaultVariant":"on","targeting":{}}}}}}'
```

### Flag 비활성화 (긴급 롤백)
```bash
oc patch featureflag welcome-flags -n open-feature-test \
  --type=merge \
  -p '{"spec":{"flagSpec":{"flags":{"welcome-page-redesign":{"state":"DISABLED"}}}}}'
```

### Flag 실시간 수정
```bash
oc edit featureflag welcome-flags -n open-feature-test
```

## 🔄 업데이트

### 이미지 업데이트
```bash
# 새 이미지 빌드
oc start-build spring-petclinic --from-dir=. --follow

# Deployment 재시작
oc rollout restart deployment/spring-petclinic -n open-feature-test
```

### Flag 업데이트
```bash
# YAML 수정 후 적용
oc apply -f 02-featureflag.yaml

# 또는 직접 편집
oc edit featureflag welcome-flags -n open-feature-test
```

## 🗑️ 정리

### 리소스 삭제
```bash
# 모든 리소스 삭제
oc delete -f .

# 또는 개별 삭제
oc delete featureflag welcome-flags -n open-feature-test
oc delete featureflagsource petclinic-flags -n open-feature-test
oc delete deployment spring-petclinic -n open-feature-test
oc delete service spring-petclinic -n open-feature-test
oc delete route spring-petclinic -n open-feature-test
```

## 📚 참고

- [배포 계획서](../05-openshift-deployment-plan.md)
- [구현 완료 보고서](../04-implementation-completed.md)
- [OpenFeature Operator](https://openfeature.dev/docs/tutorials/open-feature-operator/)
