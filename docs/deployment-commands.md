# Spring PetClinic 배포 명령어 가이드

> **Namespace**: open-feature-test
> **레지스트리**: default-route-openshift-image-registry.apps.kyobo.mtp.local

## 📑 목차

- [배포 준비](#배포-준비)
- [수동 배포](#수동-배포)
- [자동 배포](#자동-배포)
- [배포 확인](#배포-확인)
- [문제 해결](#문제-해결)

---

## 배포 준비

### 1. Namespace 확인 및 전환

```bash
# Namespace 존재 확인
oc get project open-feature-test

# Namespace 전환
oc project open-feature-test

# 현재 Namespace 확인
oc project -q
```

### 2. ImageStream 확인

```bash
# ImageStream 확인
oc get imagestream spring-petclinic -n open-feature-test

# 이미지 태그 확인
oc get imagestreamtag -n open-feature-test | grep spring-petclinic
```

**예상 출력**:
```
NAME                                      IMAGE REFERENCE
spring-petclinic:latest                   default-route-openshift-image-registry.apps.kyobo.mtp.local/open-feature-test/spring-petclinic@sha256:xxxxx
spring-petclinic:v1.0.0                   default-route-openshift-image-registry.apps.kyobo.mtp.local/open-feature-test/spring-petclinic@sha256:xxxxx
```

---

## 수동 배포

### Step 1: Deployment 생성

```bash
# Deployment 배포
oc apply -f docs/deployment.yaml

# 배포 확인
oc get deployment spring-petclinic -n open-feature-test

# Pod 상태 확인
oc get pods -n open-feature-test -l app=petclinic
```

**예상 출력**:
```
NAME                                READY   STATUS    RESTARTS   AGE
spring-petclinic-xxxxx-xxxxx        1/1     Running   0          2m
spring-petclinic-yyyyy-yyyyy        1/1     Running   0          2m
```

### Step 2: Service 생성

```bash
# Service 배포
oc apply -f docs/service.yaml

# Service 확인
oc get service spring-petclinic -n open-feature-test

# Endpoint 확인
oc get endpoints spring-petclinic -n open-feature-test
```

**예상 출력**:
```
NAME                TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)    AGE
spring-petclinic    ClusterIP   172.30.123.45    <none>        8080/TCP   1m
```

### Step 3: Route 생성

```bash
# Route 배포
oc apply -f docs/route.yaml

# Route 확인
oc get route spring-petclinic -n open-feature-test

# Route URL 가져오기
ROUTE_URL=$(oc get route spring-petclinic -n open-feature-test -o jsonpath='{.spec.host}')
echo "애플리케이션 URL: https://$ROUTE_URL"
```

**예상 출력**:
```
NAME                HOST/PORT                                                      SERVICES           PORT   TERMINATION
spring-petclinic    spring-petclinic-open-feature-test.apps.kyobo.mtp.local       spring-petclinic   http   edge
```

---

## 자동 배포

### 스크립트 사용

```bash
# docs 디렉토리로 이동
cd docs

# 실행 권한 부여
chmod +x deploy-app.sh

# 스크립트 실행
./deploy-app.sh
```

### 스크립트 출력 예시

```
======================================
Spring PetClinic 배포
======================================
Namespace: open-feature-test
이미지: default-route-openshift-image-registry.apps.kyobo.mtp.local/open-feature-test/spring-petclinic:latest
======================================
✓ Namespace 전환 중...
Now using project "open-feature-test" on server "https://api.kyobo.mtp.local:6443".
✓ Step 1: Deployment 생성 중...
deployment.apps/spring-petclinic created
✓ Step 2: Service 생성 중...
service/spring-petclinic created
✓ Step 3: Route 생성 중...
route.route.openshift.io/spring-petclinic created
✓ Step 4: 배포 대기 중 (최대 5분)...
Waiting for deployment "spring-petclinic" rollout to finish: 0 of 2 updated replicas are available...
Waiting for deployment "spring-petclinic" rollout to finish: 1 of 2 updated replicas are available...
deployment "spring-petclinic" successfully rolled out

✓ Step 5: 배포 상태 확인...
======================================
NAME                                    READY   STATUS    RESTARTS   AGE
pod/spring-petclinic-xxxxx-xxxxx        1/1     Running   0          2m
pod/spring-petclinic-yyyyy-yyyyy        1/1     Running   0          2m

NAME                       TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
service/spring-petclinic   ClusterIP   172.30.123.45   <none>        8080/TCP   2m

NAME                               READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/spring-petclinic   2/2     2            2           2m

NAME                                          DESIRED   CURRENT   READY   AGE
replicaset.apps/spring-petclinic-xxxxx        2         2         2       2m

======================================
✅ 배포 완료!
======================================
애플리케이션 URL: https://spring-petclinic-open-feature-test.apps.kyobo.mtp.local

확인 명령어:
  Health Check: curl -k https://spring-petclinic-open-feature-test.apps.kyobo.mtp.local/actuator/health
  로그 확인: oc logs -f -n open-feature-test -l app=petclinic
  Pod 상태: oc get pods -n open-feature-test -l app=petclinic
======================================
```

---

## 배포 확인

### 1. 전체 리소스 확인

```bash
# 모든 리소스 확인
oc get all -n open-feature-test -l app=petclinic
```

### 2. Pod 상태 확인

```bash
# Pod 목록
oc get pods -n open-feature-test -l app=petclinic

# Pod 상세 정보
oc describe pod -n open-feature-test -l app=petclinic

# Pod 로그 확인
oc logs -f -n open-feature-test -l app=petclinic
```

### 3. Health Check

```bash
# Route URL 가져오기
ROUTE_URL=$(oc get route spring-petclinic -n open-feature-test -o jsonpath='{.spec.host}')

# Health Check
curl -k https://$ROUTE_URL/actuator/health

# Liveness Probe
curl -k https://$ROUTE_URL/actuator/health/liveness

# Readiness Probe
curl -k https://$ROUTE_URL/actuator/health/readiness
```

**정상 응답**:
```json
{"status":"UP"}
```

### 4. 웹 브라우저 접속

```bash
# URL 출력
echo "https://$(oc get route spring-petclinic -n open-feature-test -o jsonpath='{.spec.host}')"
```

브라우저에서 위 URL로 접속하여 Spring PetClinic 메인 페이지 확인

---

## 문제 해결

### 1. Pod가 ImagePullBackOff 상태

```bash
# Pod 상세 정보 확인
oc describe pod -n open-feature-test -l app=petclinic

# ImageStream 확인
oc get imagestream spring-petclinic -n open-feature-test

# Deployment 이미지 경로 수정
oc set image deployment/spring-petclinic -n open-feature-test \
  petclinic=default-route-openshift-image-registry.apps.kyobo.mtp.local/open-feature-test/spring-petclinic:latest
```

### 2. Pod가 CrashLoopBackOff 상태

```bash
# Pod 로그 확인
oc logs -n open-feature-test -l app=petclinic --tail=100

# 이전 컨테이너 로그 확인 (재시작된 경우)
oc logs -n open-feature-test -l app=petclinic --previous

# 이벤트 확인
oc get events -n open-feature-test --sort-by='.lastTimestamp' | grep petclinic
```

### 3. Readiness Probe 실패

```bash
# Pod 직접 접속하여 Health Endpoint 테스트
POD_NAME=$(oc get pods -n open-feature-test -l app=petclinic -o jsonpath='{.items[0].metadata.name}')
oc exec -n open-feature-test $POD_NAME -- curl -s localhost:8080/actuator/health/readiness

# Readiness Probe 초기 대기 시간 증가
oc patch deployment spring-petclinic -n open-feature-test --type='json' \
  -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/readinessProbe/initialDelaySeconds", "value": 60}]'
```

### 4. Route 접속 불가

```bash
# Service Endpoint 확인
oc get endpoints spring-petclinic -n open-feature-test

# Pod 직접 테스트
POD_NAME=$(oc get pods -n open-feature-test -l app=petclinic -o jsonpath='{.items[0].metadata.name}')
oc exec -n open-feature-test $POD_NAME -- curl -s localhost:8080

# Route 상세 정보
oc describe route spring-petclinic -n open-feature-test
```

### 5. 배포 롤백

```bash
# 배포 히스토리 확인
oc rollout history deployment/spring-petclinic -n open-feature-test

# 이전 버전으로 롤백
oc rollout undo deployment/spring-petclinic -n open-feature-test

# 특정 리비전으로 롤백
oc rollout undo deployment/spring-petclinic -n open-feature-test --to-revision=1
```

---

## 리소스 삭제

### 개별 삭제

```bash
# Route 삭제
oc delete route spring-petclinic -n open-feature-test

# Service 삭제
oc delete service spring-petclinic -n open-feature-test

# Deployment 삭제
oc delete deployment spring-petclinic -n open-feature-test
```

### 라벨 기반 일괄 삭제

```bash
# 모든 리소스 삭제
oc delete all -n open-feature-test -l app=petclinic

# 확인
oc get all -n open-feature-test -l app=petclinic
```

### YAML 파일 기반 삭제

```bash
# YAML 파일로 삭제
oc delete -f docs/route.yaml
oc delete -f docs/service.yaml
oc delete -f docs/deployment.yaml
```

---

## 추가 작업

### 스케일링

```bash
# Replica 수 증가
oc scale deployment spring-petclinic -n open-feature-test --replicas=3

# 확인
oc get pods -n open-feature-test -l app=petclinic

# Auto-scaling 설정
oc autoscale deployment spring-petclinic -n open-feature-test \
  --min=2 --max=5 \
  --cpu-percent=70
```

### 이미지 업데이트

```bash
# 새 이미지로 업데이트
oc set image deployment/spring-petclinic -n open-feature-test \
  petclinic=default-route-openshift-image-registry.apps.kyobo.mtp.local/open-feature-test/spring-petclinic:v1.0.1

# 롤아웃 상태 확인
oc rollout status deployment/spring-petclinic -n open-feature-test
```

### 리소스 모니터링

```bash
# 리소스 사용량
oc adm top pods -n open-feature-test -l app=petclinic

# 실시간 로그
oc logs -f -n open-feature-test -l app=petclinic --all-containers=true

# 이벤트 모니터링
watch -n 2 "oc get events -n open-feature-test --sort-by='.lastTimestamp' | tail -20"
```

---

## 체크리스트

- [ ] Namespace 확인 (open-feature-test)
- [ ] ImageStream 확인
- [ ] Deployment 배포
- [ ] Pod Running 확인 (2/2)
- [ ] Service 배포
- [ ] Service Endpoint 확인
- [ ] Route 배포
- [ ] Route URL 확인
- [ ] Health Check 성공
- [ ] 웹 브라우저 접속 확인
- [ ] 애플리케이션 정상 동작 확인

---

## 참고

### YAML 파일 위치

- `docs/deployment.yaml` - Deployment 정의
- `docs/service.yaml` - Service 정의
- `docs/route.yaml` - Route 정의
- `docs/deploy-app.sh` - 자동 배포 스크립트

### 주요 설정

- **Namespace**: open-feature-test
- **Replicas**: 2
- **Memory**: 512Mi (request) / 1Gi (limit)
- **CPU**: 500m (request) / 1000m (limit)
- **Port**: 8080
- **Health Check Path**: /actuator/health
