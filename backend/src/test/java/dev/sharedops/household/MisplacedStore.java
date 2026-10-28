package dev.sharedops.household;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("architecture-fixture")
public class MisplacedStore {}
