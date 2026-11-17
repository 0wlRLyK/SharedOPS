# Research — Cross-household access denial

**Status** Draft

**Scope** HO-93 validation of HO-10 AC-14 only

## Established by the baseline

- **Behavior.** AC-14 requires no foreign data return or mutation. NFR-06, the constitution and HO-10 supply the active-membership authorization boundary. The authorized B control comes from the requested slice. [Requirements](../../docs/requirements.md) · [Constitution](../../.specify/memory/constitution.md) · [Specification](spec.md)
- **Public denial.** ADR-0008 requires foreign and absent resources to be indistinguishable. OpenAPI documents planned `404 NOT_FOUND` for both with `application/problem+json`. The planned Task GET is a read candidate. OpenAPI lists no Task mutation. [ADR-0008](../../docs/adr/0008-rest-api-contract-and-problem-details.md) · [OpenAPI](../../docs/api/openapi.yml)
- **Committed-state proof.** T30 calls for API and application-use-case checks and PostgreSQL verification through a fresh persistence context or connection after transaction completion. An HTTP rejection by itself establishes only the response. [Test strategy](../../docs/test-strategy.md)

## Deferred decisions

| ID  | Decision and rationale                                                                                                                                                                                                                               | Alternatives to review                                                                                                                                                    |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | Select a resource and representative read and mutation that exercise real entry points. The contract gives a planned read and no Task mutation, so it cannot establish a runnable pair. Confirm relevant identifier inputs and runtime availability. | Consider the planned Task read with a separately reviewed real Task mutation when available, or a different same-resource read and mutation pair that meets the baseline. |
| D2  | Specify the public comparison for the approved pair. The existing contract supplies shared semantics, with operation-level fields, headers and permitted incidental variation still to be defined.                                                   | Compare exact stable fields and any relevant headers, with narrow treatment of request-specific metadata if approved and demonstrated independent of existence.           |
| D3  | Specify the committed state affected by the selected mutation and the positive control's expected delta. This depends on D1.                                                                                                                         | Compare the target and each applicable related or audit record the write could affect. Select concrete fields and queries once the operation is known.                    |
