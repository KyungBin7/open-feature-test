# OpenFeature A/B Testing Documentation

이 문서는 Spring PetClinic 프로젝트에 OpenFeature를 도입하여 메인 페이지 A/B 테스트를 수행하는 방법을 설명합니다.

## 📚 문서 구조

1. **[01-research.md](01-research.md)** - OpenFeature 리서치 결과
   - OpenFeature 개요 및 아키텍처
   - OpenShift Service Mesh vs OpenFeature 비교
   - Provider 비교 및 선택 가이드

2. **[02-implementation-guide.md](02-implementation-guide.md)** - 구현 가이드
   - Spring Boot 애플리케이션 수정
   - OpenFeature SDK 통합
   - Feature Flag 평가 로직 구현
   - 테스트 코드 작성

3. **[03-deployment-guide.md](03-deployment-guide.md)** - 배포 가이드
   - OpenShift 환경 설정
   - OpenFeature Operator 설치
   - FeatureFlag CRD 정의
   - A/B 테스트 전략 및 모니터링

## 🎯 프로젝트 목표

### A/B 테스트 시나리오
- **Variant A (Control)**: 기존 메인 페이지 디자인
- **Variant B (Treatment)**: 새로운 메인 페이지 디자인

### 성공 지표
- 에러율 감소
- 페이지 로딩 시간 개선
- 사용자 전환율 증가
- 사용자 체류 시간 증가

## 🚀 빠른 시작

```bash
# 1. 문서 읽기 순서
cat docs/openfeature/01-research.md
cat docs/openfeature/02-implementation-guide.md
cat docs/openfeature/03-deployment-guide.md

# 2. OpenFeature Operator 설치 (OpenShift)
helm install openfeature-operator openfeature/open-feature-operator \
  -n openfeature-operator-system --create-namespace

# 3. 구현 시작 (02-implementation-guide.md 참고)
# - pom.xml 의존성 추가
# - OpenFeatureConfig.java 작성
# - WelcomeController.java 수정

# 4. 배포 (03-deployment-guide.md 참고)
oc apply -f openshift/
```

## 🔧 기술 스택

- **OpenFeature SDK**: v1.15.1
- **flagd Provider**: v0.11.10
- **OpenFeature Operator**: v0.6.0
- **Spring Boot**: 3.5.0
- **Java**: 17
- **Platform**: OpenShift 4.x

## 📊 A/B 테스트 타임라인

| Week | Variant B Traffic | Activity |
|------|-------------------|----------|
| Week 1 | 10% | 기술적 안정성 검증 |
| Week 2 | 50% | 통계적 유의성 확보 |
| Week 3 | 100% or 0% | 의사결정 및 롤아웃 |

## ⚠️ 중요 고려사항

1. **최소 샘플 사이즈**: 각 variant당 최소 1,000명
2. **테스트 기간**: 최소 1주일 (주중/주말 포함)
3. **통계적 유의성**: 95% 신뢰 수준 (p-value < 0.05)
4. **롤백 계획**: CRD 수정으로 즉시 롤백 가능

## 📞 문의

구현 중 문제가 발생하면 다음 리소스를 참고하세요:

- [OpenFeature 공식 문서](https://openfeature.dev/)
- [flagd 공식 문서](https://flagd.dev/)
- [OpenFeature Operator GitHub](https://github.com/open-feature/open-feature-operator)

## 📝 라이선스

이 문서는 Apache License 2.0에 따라 배포됩니다.
