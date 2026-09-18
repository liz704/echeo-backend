package com.echeo.repository;

import com.echeo.model.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("SELECT g FROM Group g JOIN FETCH g.owner WHERE g.owner.id = :ownerId")
    List<Group> findByOwner_Id(@Param("ownerId") Long ownerId);

    @Query("SELECT g FROM Group g JOIN FETCH g.owner WHERE g.id = :id")
    Optional<Group> findByIdWithOwner(@Param("id") Long id);
}