# ADR-0008: Maintain a Contract-First REST API

Date: 04.11.2026
Status: Accepted
**Scope:** SharedOPS HTTP API

## Context

SharedOPS needs a client-agnostic API with stable, machine-readable errors (NFR-04 and NFR-05). Backend operations and their consumers are delivered incrementally, so a contract may describe an operation before it exists. Clients need a reliable source for the intended behavior and a clear distinction between planned and implemented functionality.

Commands also need consistent stale-write behavior, while errors must not reveal sensitive data or the existence of another household's resources. HO-7 establishes the API contract and error-handling foundation; feature tickets deliver the associated workflows.

## Decision

- Maintain [the hand-authored OpenAPI document](../api/openapi.yml) as the source of truth for the public API contract. Review contract changes alongside implementation changes. Mark planned operations and responses explicitly; their presence does not imply runtime availability.
- Use [RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html) for structured HTTP error responses, with `application/problem+json` for JSON. RFC 9457 supersedes RFC 7807. Its standard members and their semantics apply; `code` and `fieldErrors` are SharedOPS extension members, not standard RFC members.
- Document stable application codes and field errors in OpenAPI. Clients may use `code` to distinguish SharedOPS error conditions; human-readable messages are not machine identifiers. The RFC `type` URI remains the problem type's primary identifier; when omitted it defaults to `about:blank`. SharedOPS codes provide application-specific detail without redefining the RFC `type` semantics.
- Do not expose rejected values, secrets, stack traces or database details. Responses for inaccessible and absent household resources must not reveal which case applies. Input normalization is specified per field; passwords are not silently rewritten.
- Use the expected version in the command body for optimistic concurrency. A stale version returns `409 Conflict` without changing state. Do not introduce `If-Match` as a conflicting concurrency contract; HTTP preconditions have different semantics.
- Controllers delegate business decisions to application use cases. Add contract and security tests for each implemented operation, including negative cases and persisted state where relevant.

## Alternatives Considered

- **Generated OpenAPI as the source of truth:** reduces manual synchronization, but cannot alone specify and review operations before their implementation. Springdoc output remains separate and is not the authoritative contract.
- **A proprietary error envelope:** could encode the same data, but would require clients to learn a bespoke HTTP error format instead of reusing Problem Details tooling and conventions.
- **HTTP `If-Match` for concurrency:** a valid HTTP precondition mechanism, but it differs from the body-version and `409` semantics selected for this API. Mixing both would create ambiguous behavior for clients.

## Consequences

Contract-first documentation supports early client design and review across backend and frontend. Maintaining it manually creates a risk of drift, so changes need review and automated checks; the current test checks selected contract properties, not complete OpenAPI validation or all runtime operations. Adding or changing application codes and field semantics requires compatibility review.

The contract describes future behavior where explicitly marked. RFC 9457 does not itself define SharedOPS codes, normalize inputs, enforce authorization, or guarantee transaction rollback; those remain application responsibilities.

## Verification

- HO-7: parse the manual contract and exercise implemented error handling through MVC tests, including malformed inputs and field paths. The current contract test checks selected properties and does not validate the full OpenAPI specification.
- Feature tickets: test each implemented operation against the contract, including authentication, household isolation, normalization and applicable error responses. HO-9 must verify password preservation through the real authentication flow.
- Command tickets: verify a stale body version returns `409` and leaves persisted state unchanged, including concurrent requests where appropriate. Rate-limit and not-found behavior remain pending their respective implementations.

## Revisit Trigger

Reconsider this decision if contract drift cannot be controlled, clients require a different versioning mechanism, or a new interoperability requirement warrants generated contracts or specific problem type URIs.
