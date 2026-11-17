# Validation Data Model — Cross-household access denial

**Status** Draft

**Scope** HO-93 validation of HO-10 AC-14 only

**Input** [Specification](spec.md) · [Open decisions](research.md)

| Fixture                     | Relationship and constraint                                                                                                                                             |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Household A and Household B | A and B are two distinct households.                                                                                                                                    |
| A actor and B actor         | Each actor has a distinct authenticated identity with active membership in its corresponding household. A lacks B access. B has permission for the selected operations. |
| B resource                  | The existing resource is owned by B and committed before the operation.                                                                                                 |
| Absent comparator           | The absent identifier has the same valid form as the B resource identifier and refers to no resource.                                                                   |
| Committed observations      | Observe the target and applicable related committed data before and after the mutation. Use separate fixture state for each mutation scenario.                          |

## State transitions

- A's rejected mutation leaves the committed target and applicable related data identical to the baseline. Include any applicable audit or related records when establishing the operation's footprint.
- A permitted B mutation produces the expected committed change on a separate equivalent fixture.
- Observe post-operation data after the operation transaction finishes using a fresh persistence context or connection.

D1 and D3 determine the concrete entities, command data and persisted fields. This artifact defines validation fixtures and proposes no schema or migration.
