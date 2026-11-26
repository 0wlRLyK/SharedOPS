@ApplicationModule(
    allowedDependencies = {
      "responsibility",
      "responsibility::task",
      "household::lookup",
      "household::task"
    })
package dev.sharedops.task;

import org.springframework.modulith.ApplicationModule;
