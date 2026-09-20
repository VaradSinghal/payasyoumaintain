# Contracts Service

**Tech Stack:** Java 21 / Spring Boot

## Responsibility

This service manages smart contract and policy document lifecycle. It handles:

- **Policy Document Generation** — producing PDF policy documents, endorsements, and certificates of insurance.
- **Digital Signatures** — orchestrating e-signature workflows for policy binding.
- **Contract Versioning** — maintaining an immutable audit trail of all contract versions and amendments.
- **Regulatory Compliance** — ensuring generated documents meet jurisdictional regulatory requirements.
- **Template Management** — managing document templates for different products, regions, and languages.

## API Contracts

_To be defined._

## Dependencies

- PostgreSQL (contract metadata)
- Object Storage / S3 (generated documents)
