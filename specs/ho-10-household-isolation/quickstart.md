# Validation Quickstart — Cross-household access denial

**Status** Draft, runtime validation pending

**Scope** HO-93 validation of HO-10 AC-14 only

## Prerequisites

Resolve D1–D3, obtain owner approval of the spec and plan, and confirm the selected operations and tests are available. Use the [pinned toolchain](../../docs/stack.md), a Docker-compatible runtime, PostgreSQL with production Flyway migrations and the [synthetic fixture](data-model.md). Use valid authenticated sessions and otherwise valid operation inputs. For unsafe HTTP requests include the appropriate CSRF token where the contract requires it.

## Scenarios to run after approval

| Scenario | Procedure                                                                                                                                                                                                                   | Expected evidence                                                                                                          |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| S1       | As A, use the selected read against the committed B fixture. Cover the HTTP path and the real application use case.                                                                                                         | Denial at both boundaries. HTTP returns no B data and follows the approved [contract assertions](contracts/ac-14.md).      |
| S2       | Capture baseline data, submit the selected otherwise valid mutation as A, then compare committed state after each invocation at the HTTP and real application boundaries using fresh observations and independent fixtures. | Both invocations are denied before side effects, and each committed observation matches the baseline.                      |
| S3       | As A, repeat the selected read and mutation at HTTP using a valid absent identifier and otherwise equivalent inputs.                                                                                                        | Each existing/absent response pair satisfies the approved comparator.                                                      |
| S4       | As B, perform the selected read and valid mutation on independent equivalent fixtures.                                                                                                                                      | The read returns the expected resource. The mutation succeeds and a fresh committed observation shows the approved change. |

After approval and once the selected test exists, perform T010 in an isolated worktree. Record a passing baseline, the intended authorization assertion failing with the membership check removed, and a passing result after restoration. Do not commit the intentional defect. This is future HO-10 work.

After T010, when the tests are available, run the applicable repository gates from the root. The planned focused command uses the candidate integration test name and applies only if D1 confirms it.

```sh
make check
make test
(cd backend && ./mvnw verify -Dit.test=HouseholdIsolationIT)
make verify
```

Record HO-10 AC-14/NFR-06 and T30, scenario and test IDs, expected and actual results, exact commands, tested revision, tool and database versions, run time, and sanitized response and committed-state evidence. Report missing tests, failures, skips and blockages explicitly. Runtime checks and evidence are pending in this draft.

After implementation and verification, run `$speckit-converge` and reconcile the diff and evidence with the approved specification before owner handoff. For an absent-ID mutation, compare committed state and applicable side effects identified in D3 with the baseline.
