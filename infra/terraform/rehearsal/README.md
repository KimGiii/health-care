# G2 전량 적재 용량 리허설 — 일회성 RDS 스택

prod 본 적재 전, 식품 카탈로그 전량 적재를 **prod 패리티 엔진(PG17) + 운영 적재창 클래스(db.t3.medium)** 에서
1회 리허설해 용량·소요·정합성을 측정하기 위한 **쓰고 버리는** Terraform 스택이다.

- 전체 절차·판단 기준: [docs/operations/FOOD_CATALOG_BULK_LOAD_RDS_REHEARSAL.md](../../../docs/operations/FOOD_CATALOG_BULK_LOAD_RDS_REHEARSAL.md)
- prod 스택(`../aws`)·dev 스택(`../dev`)과 **state·VPC 모두 분리**. 이 스택을 apply 해도 prod 에 영향 없음.

## 왜 별도 스택인가

- **일회성**: 측정 후 `terraform destroy` 로 완전 삭제(상시 dev RDS 신설 회피 — 비용/토폴로지 결정 참고).
- **격리**: 전용 VPC(`10.9.0.0/16`) + 전용 SG. 5432 인바운드는 `allowed_cidrs`(로더 IP /32)로만 개방.
- **ephemeral-safe**: `deletion_protection=false`, `skip_final_snapshot=true`, `backup_retention_period=0` → 항상 destroy 가능.

## 사용

```bash
cd infra/terraform/rehearsal
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars 편집: allowed_cidrs = ["$(curl -s https://checkip.amazonaws.com)/32"], db_username/db_password

terraform init
terraform apply            # db.t3.medium PG17 기동 (~수 분)
terraform output jdbc_url  # 로더 백엔드 DB_URL 로 사용
```

로더(백엔드)는 별도 EC2 또는 로컬에서 다음 env 로 기동(이 빌드에 V39+dedup 코드 포함 → Flyway 가 인스턴스에 스키마 적용):

```bash
DB_URL=<output jdbc_url> DB_USERNAME=<tfvars> DB_PASSWORD=<tfvars> \
  SPRING_PROFILES_ACTIVE=prod ADMIN_OPERATION_TOKEN=<token> PUBLIC_FOOD_API_KEY=<key> \
  ./gradlew bootRun
```

이후 적재·측정은 운영 절차 문서의 절차표(4~8번)를 따른다.

## 측정 끝나면 (필수)

```bash
terraform destroy
```

> CloudWatch/Performance Insights 로 `FreeableMemory`·`WriteIOPS`·`FreeStorageSpace`·CPU 크레딧을 적재 중 기록한 뒤 삭제한다.
> 스토리지가 20GB 를 넘겨 오토스케일되면(비가역) 본 적재 비용 영향 신호다.
