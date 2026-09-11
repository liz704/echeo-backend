package com.echeo.repository;

import com.echeo.model.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByOwner_Id(Long ownerId);
}
