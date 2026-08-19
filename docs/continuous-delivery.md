# Continuous delivery to AWS EC2

The `Deploy to AWS EC2` workflow turns a selected commit into five immutable
container images and deploys them to the existing EC2 Docker Compose host.

```text
workflow_dispatch
       |
       v
Maven verify (5 services)
       |
       v
Docker build + push to ECR (commit SHA)
       |
       v
GitHub OIDC -> temporary AWS role
       |
       v
Systems Manager Run Command
       |
       v
EC2: pull -> compose up -> health checks
                         |
                    failure -> rollback
```

The workflow is deliberately manual because the portfolio EC2 is normally
stopped to control cost. The deployment itself is automated and repeatable.
Changing the trigger to a successful `main` build would make it continuous
deployment rather than manually approved continuous delivery.

## Security model

- GitHub exchanges its OIDC token for temporary AWS credentials.
- The IAM trust policy accepts only `main` from this repository.
- GitHub may push only to the five project ECR repositories.
- GitHub may run only `AWS-RunShellScript` on the configured EC2 instance.
- EC2 may pull only from the five project ECR repositories.
- EC2 is controlled through SSM, so the workflow stores no SSH private key.
- Production application secrets remain in `/opt/event-driven-ecommerce/.env`.
- SSM commands never include application secrets.

The unavoidable `Resource = "*"` permissions are limited to APIs that do not
support useful resource scoping here: ECR authorization, STS caller identity,
and SSM command-status discovery. Image push/pull and command execution are
restricted to explicit repositories, the managed SSM document, and the target
EC2 instance.

## Prerequisites

Local provisioning workstation:

- AWS CLI authenticated as an identity allowed to manage IAM and ECR;
- Terraform;
- the target EC2 instance ID.

Existing EC2 host:

- Ubuntu or another Linux distribution;
- Docker Engine and Docker Compose v2;
- AWS CLI, Git and curl;
- SSM Agent;
- outbound HTTPS access to AWS APIs, ECR and GitHub;
- enough disk and memory for all containers.

The deployment does not require inbound SSH. The existing application ports
remain controlled by the EC2 security group.

## 1. Provision ECR and IAM

```bash
cd infrastructure/aws/cd
cp terraform.tfvars.example terraform.tfvars
```

Set the real `ec2_instance_id` in `terraform.tfvars`.

If the EC2 already has an IAM role, set:

```hcl
existing_ec2_role_name = "existing-ec2-role-name"
```

Terraform will attach the SSM managed policy and a project-scoped ECR pull
policy to that role.

If GitHub OIDC already exists in the AWS account, use:

```hcl
create_github_oidc_provider = false
github_oidc_provider_arn    = "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
```

Review before creating resources:

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan -out cd.tfplan
terraform apply cd.tfplan
```

Do not commit `terraform.tfvars`, state, or plans.

### Newly created EC2 instance profile

When `existing_ec2_role_name` is empty, Terraform creates an instance profile.
Associate it with the existing EC2 once:

```bash
aws ec2 associate-iam-instance-profile \
  --region sa-east-1 \
  --instance-id i-REPLACE_ME \
  --iam-instance-profile Name=event-driven-ecommerce-ec2
```

An EC2 instance can have only one instance profile. If one is already attached,
prefer supplying its role name to Terraform instead of replacing it.

## 2. Bootstrap the EC2 application directory

After the instance appears `Online` in Systems Manager, clone the repository if
it is not already present and run the bootstrap script once through SSM or an
existing terminal:

```bash
sudo git clone \
  https://github.com/ToniniTech/Event-Driven-E-commerce.git \
  /opt/event-driven-ecommerce

sudo bash /opt/event-driven-ecommerce/scripts/deployment/bootstrap-ec2.sh \
  https://github.com/ToniniTech/Event-Driven-E-commerce.git \
  /opt/event-driven-ecommerce
```

Edit `/opt/event-driven-ecommerce/.env` and replace every placeholder before
deploying. The script preserves an existing `.env` and never prints its values.

Verify SSM connectivity:

```bash
aws ssm describe-instance-information \
  --filters Key=InstanceIds,Values=i-REPLACE_ME
```

The instance must report `PingStatus` equal to `Online`.

## 3. Configure GitHub repository variables

Terraform prints most values through the `github_actions_variables` output.
Create these non-secret repository variables under **Settings -> Secrets and
variables -> Actions -> Variables**:

| Variable | Example |
|---|---|
| `AWS_REGION` | `sa-east-1` |
| `AWS_DEPLOY_ROLE_ARN` | Terraform output `github_deploy_role_arn` |
| `EC2_INSTANCE_ID` | `i-0123456789abcdef0` |
| `EC2_APP_DIR` | `/opt/event-driven-ecommerce` |

No GitHub Actions secret is required for AWS authentication.

## 4. Run a deployment

1. Start the EC2 instance.
2. Wait until Systems Manager reports it `Online`.
3. Open **Actions -> Deploy to AWS EC2 -> Run workflow**.
4. Select `main`.
5. Enter `DEPLOY` exactly.
6. Run the workflow.

For each service the workflow:

1. runs `mvn verify`;
2. builds its Docker image;
3. pushes both the immutable commit SHA and convenience `latest` tag;
4. sends one SSM deployment command;
5. pulls the SHA-tagged images;
6. recreates the Compose application;
7. verifies all five Actuator health endpoints.

The deployed tag is stored on EC2 in `.deployed-image-tag`. It is operational
state, not a secret.

## Rollback behavior

If any application service does not become healthy, the remote script:

1. prints container and health status without exporting application logs;
2. reads the previous successful SHA from `.deployed-image-tag`;
3. pulls those five images;
4. recreates the application with the previous SHA;
5. verifies health again;
6. leaves the GitHub deployment marked failed even after a successful rollback.

The first deployment cannot roll back because no previous successful tag exists.

## Important database limitation

Every service currently uses Hibernate `ddl-auto: update`. Container rollback
therefore does not undo a schema change. Avoid incompatible schema changes in
this workflow. A production hardening step is to replace automatic DDL with
versioned Flyway migrations that are backward-compatible with the previous
application release.

## Troubleshooting

### EC2 is not Online in SSM

Check the instance profile, SSM Agent, outbound connectivity and the
`AmazonSSMManagedInstanceCore` attachment.

### ECR pull is denied

Confirm the EC2 role has the Terraform-managed ECR pull policy and that its
region matches `AWS_REGION`.

### GitHub cannot assume the role

Confirm the workflow was dispatched from `main`. The trust policy deliberately
rejects feature branches and other repositories.

### Missing `.env`

The remote script refuses to deploy. Create the file directly on EC2 with mode
`0600`; do not transmit its values through SSM commands or commit it.

### Health verification fails

Inspect the SSM command output and, on EC2:

```bash
cd /opt/event-driven-ecommerce
docker compose ps
docker compose logs --tail 100
```

## Cost and cleanup

ECR storage and the running EC2 instance can generate cost. The lifecycle
policies retain a bounded image history while preserving enough versions for
rollback. Stopping EC2 does not remove EBS storage cost.

Do not destroy the Terraform stack while the deployment still relies on its ECR
repositories and IAM roles. ECR repositories use `force_delete = false`, so
Terraform refuses to delete repositories that still contain images.
