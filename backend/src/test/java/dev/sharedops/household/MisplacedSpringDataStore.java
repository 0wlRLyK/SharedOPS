package dev.sharedops.household;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface MisplacedSpringDataStore extends Repository<Object, Long> {}
