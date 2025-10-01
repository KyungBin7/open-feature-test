#!/bin/bash

# Spring PetClinic 배포 스크립트
# Namespace: open-feature-test

PROJECT="open-feature-test"
REGISTRY="default-route-openshift-image-registry.apps.kyobo.mtp.local"
IMAGE_NAME="spring-petclinic"

echo "======================================"
echo "Spring PetClinic 배포"
echo "======================================"
echo "Namespace: $PROJECT"
echo "이미지: $REGISTRY/$PROJECT/$IMAGE_NAME:latest"
echo "======================================"

# 현재 프로젝트 전환
echo "✓ Namespace 전환 중..."
oc project $PROJECT
if [ $? -ne 0 ]; then
  echo "❌ Namespace '$PROJECT'가 존재하지 않습니다."
  echo "다음 명령어로 생성하세요: oc new-project $PROJECT"
  exit 1
fi

# 1. Deployment 생성
echo "✓ Step 1: Deployment 생성 중..."
oc apply -f deployment.yaml
if [ $? -ne 0 ]; then
  echo "❌ Deployment 생성 실패"
  exit 1
fi

# 2. Service 생성
echo "✓ Step 2: Service 생성 중..."
oc apply -f service.yaml
if [ $? -ne 0 ]; then
  echo "❌ Service 생성 실패"
  exit 1
fi

# 3. Route 생성
echo "✓ Step 3: Route 생성 중..."
oc apply -f route.yaml
if [ $? -ne 0 ]; then
  echo "❌ Route 생성 실패"
  exit 1
fi

# 4. 배포 상태 대기
echo "✓ Step 4: 배포 대기 중 (최대 5분)..."
oc rollout status deployment/spring-petclinic -n $PROJECT --timeout=5m
if [ $? -ne 0 ]; then
  echo "⚠️  배포가 완료되지 않았습니다. 상태를 확인하세요."
  echo "명령어: oc get pods -n $PROJECT -l app=petclinic"
fi

# 5. 확인
echo ""
echo "✓ Step 5: 배포 상태 확인..."
echo "======================================"
oc get all -n $PROJECT -l app=petclinic

# 6. Route URL 출력
echo ""
ROUTE_URL=$(oc get route spring-petclinic -n $PROJECT -o jsonpath='{.spec.host}' 2>/dev/null)
if [ -z "$ROUTE_URL" ]; then
  echo "⚠️  Route가 아직 생성되지 않았습니다."
else
  echo "======================================"
  echo "✅ 배포 완료!"
  echo "======================================"
  echo "애플리케이션 URL: https://$ROUTE_URL"
  echo ""
  echo "확인 명령어:"
  echo "  Health Check: curl -k https://$ROUTE_URL/actuator/health"
  echo "  로그 확인: oc logs -f -n $PROJECT -l app=petclinic"
  echo "  Pod 상태: oc get pods -n $PROJECT -l app=petclinic"
  echo "======================================"
fi
