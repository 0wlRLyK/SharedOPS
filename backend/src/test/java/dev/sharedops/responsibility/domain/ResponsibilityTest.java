package dev.sharedops.responsibility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResponsibilityTest {
  private final UUID id = UUID.randomUUID();
  private final UUID household = UUID.randomUUID();
  private final UUID owner = UUID.randomUUID();

  private Responsibility create() {
    return Responsibility.create(id, household, "  Laundry  ", null, owner);
  }

  @Test
  void rejectsZeroBytesInTitleAndDescriptionOnCreateAndEdit() {
    assertThatThrownBy(() -> Responsibility.create(id, household, "a\0b", null, owner))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Responsibility.create(id, household, "title", "a\0b", owner))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create().edit("a\0b", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create().edit("title", "a\0b"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(create().edit("title", "a\u0001b").description()).isEqualTo("a\u0001b");
  }

  @Test
  void creationPreservesTitleAndInitializesActiveIndividualVersionZero() {
    var item = create();
    assertThat(item.id()).isEqualTo(id);
    assertThat(item.householdId()).isEqualTo(household);
    assertThat(item.ownerMembershipId()).isEqualTo(owner);
    assertThat(item.title()).isEqualTo("  Laundry  ");
    assertThat(item.description()).isNull();
    assertThat(item.mode()).isEqualTo(ResponsibilityMode.INDIVIDUAL);
    assertThat(item.status()).isEqualTo(ResponsibilityStatus.ACTIVE);
    assertThat(item.version()).isZero();
  }

  @Test
  void rejectsBlankTitleMissingIdentityAndSharedMode() {
    for (String invalid : new String[] {null, "", " \t\n", "\u1680\u2003\u2028\u3000", "\u001c"}) {
      assertThatThrownBy(() -> Responsibility.create(id, household, invalid, null, owner))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> Responsibility.create(id, household, "title", null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                Responsibility.rehydrate(
                    id,
                    household,
                    "title",
                    null,
                    ResponsibilityMode.SHARED,
                    ResponsibilityStatus.ACTIVE,
                    owner,
                    0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Responsibility.rehydrate(
                    id,
                    household,
                    "title",
                    null,
                    ResponsibilityMode.INDIVIDUAL,
                    ResponsibilityStatus.ACTIVE,
                    owner,
                    -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonBreakingSpacesAndMixedUnicodeTitlesArePreserved() {
    for (String valid : new String[] {"\u00a0", "\u2007", "\u202f", "\u2003x\u2003"}) {
      assertThat(Responsibility.create(id, household, valid, null, owner).title()).isEqualTo(valid);
    }
  }

  @Test
  void detailsAndTransferChangeExactlyOnceAndNoOpsPreserveVersion() {
    var item = create();
    assertThat(item.edit("  Laundry  ", null)).isSameAs(item);
    assertThat(item.transfer(owner)).isSameAs(item);
    var edited = item.edit("Laundry", "");
    assertThat(edited.version()).isEqualTo(1);
    assertThat(edited.description()).isEmpty();
    var transferred = edited.transfer(UUID.randomUUID());
    assertThat(transferred.version()).isEqualTo(2);
    assertThat(item.version()).isZero();
    assertThatThrownBy(() -> item.edit(" ", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> item.transfer(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void lifecycleTransitionsAdvanceAndRepeatedOrIllegalTransitionsReject() {
    var active = create();
    var paused = active.pause();
    assertThat(paused.status()).isEqualTo(ResponsibilityStatus.PAUSED);
    assertThat(paused.version()).isEqualTo(1);
    assertThatThrownBy(paused::pause).isInstanceOf(Responsibility.InvalidTransition.class);
    var resumed = paused.resume();
    assertThat(resumed.status()).isEqualTo(ResponsibilityStatus.ACTIVE);
    assertThat(resumed.version()).isEqualTo(2);
    assertThatThrownBy(resumed::resume).isInstanceOf(Responsibility.InvalidTransition.class);
    var archived = paused.archive();
    assertThat(archived.status()).isEqualTo(ResponsibilityStatus.ARCHIVED);
    assertThat(archived.version()).isEqualTo(2);
    assertThatThrownBy(archived::archive).isInstanceOf(Responsibility.InvalidTransition.class);
    assertThatThrownBy(archived::pause).isInstanceOf(Responsibility.InvalidTransition.class);
    assertThatThrownBy(archived::resume).isInstanceOf(Responsibility.InvalidTransition.class);
    assertThatThrownBy(() -> archived.edit("changed", null))
        .isInstanceOf(Responsibility.InvalidTransition.class);
    assertThatThrownBy(() -> archived.transfer(owner))
        .isInstanceOf(Responsibility.InvalidTransition.class);
    var replacement = UUID.randomUUID();
    var restored = archived.restore(replacement);
    assertThat(restored.status()).isEqualTo(ResponsibilityStatus.ACTIVE);
    assertThat(restored.ownerMembershipId()).isEqualTo(replacement);
    assertThat(restored.version()).isEqualTo(3);
    assertThatThrownBy(() -> resumed.restore(owner))
        .isInstanceOf(Responsibility.InvalidTransition.class);
    assertThatThrownBy(() -> archived.restore(null)).isInstanceOf(NullPointerException.class);
    assertThat(active.archive().status()).isEqualTo(ResponsibilityStatus.ARCHIVED);
  }

  @Test
  void overflowCannotWrapVersionOrModifyOriginal() {
    var max =
        Responsibility.rehydrate(
            id,
            household,
            "Title",
            null,
            ResponsibilityMode.INDIVIDUAL,
            ResponsibilityStatus.ACTIVE,
            owner,
            Long.MAX_VALUE);
    assertThat(max.edit("Title", null)).isSameAs(max);
    assertThatThrownBy(max::pause).isInstanceOf(ArithmeticException.class);
    assertThat(max.version()).isEqualTo(Long.MAX_VALUE);
    assertThat(max.status()).isEqualTo(ResponsibilityStatus.ACTIVE);
  }
}
