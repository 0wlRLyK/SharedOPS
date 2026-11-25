# Tasks — Current account and household

**Status** Draft, task pending; specification and plan are not approved.

**Input** [Specification](spec.md). Complete the full Spec Kit workflow, including a plan, checklist, task analysis and owner approval of the spec and plan, before editing implementation or test files. The implementation approach and exact file ownership are to be resolved in that plan.

- [ ] T001 Implement `GET /api/v1/auth/me` and its OpenAPI contract according to the approved specification and plan. Return the authenticated account and its actively joined household, or `household: null` when there is no active membership; return `401 UNAUTHORIZED` for unauthenticated requests and `Cache-Control: no-store` on responses. Add the positive, negative, isolation and response-contract verification described in the specification, and record the applicable checks and results before handoff.
