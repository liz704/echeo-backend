package com.echeo.repository;

import com.echeo.model.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroup_Id(Long groupId);

    @Query("SELECT m FROM GroupMember m JOIN FETCH m.group g JOIN FETCH g.owner WHERE m.user.id = :userId")
    List<GroupMember> findByUser_Id(@Param("userId") Long userId);

    Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);

    // Un membre externe est identifié par son email au sein d'un groupe donné
    // (pas d'identifiant utilisateur puisqu'il n'a pas de compte).
    boolean existsByGroup_IdAndExternalEmail(Long groupId, String externalEmail);
}