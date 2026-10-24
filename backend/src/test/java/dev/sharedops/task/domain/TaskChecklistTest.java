package dev.sharedops.task.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TaskChecklistTest {
  @Test
  public void constructorMakesDefensiveCopy() {
    List<String> items = new ArrayList<>();
    items.add("Buy milk");

    TaskChecklist checklist = new TaskChecklist(items);

    items.add("Buy bread");

    assertThat(checklist.items()).containsExactly("Buy milk");
  }

  @Test
  public void rejectsNullItems() {
    assertThatThrownBy(() -> new TaskChecklist(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("TaskChecklist must not be null");
  }

  @Test
  public void itemsCannotBeModified() {
    List<String> items = new ArrayList<>();
    items.add("Buy milk");

    TaskChecklist checklist = new TaskChecklist(items);
    assertThatThrownBy(() -> checklist.items().add("Buy bread"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void equalChecklistsHaveEqualHashes() {
    TaskChecklist first = new TaskChecklist(new ArrayList<>(List.of("Buy milk")));

    TaskChecklist second = new TaskChecklist(new ArrayList<>(List.of("Buy milk")));

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }
}
