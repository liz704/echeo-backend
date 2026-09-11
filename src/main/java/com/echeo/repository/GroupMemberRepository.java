package com.echeo.repository;

import com.echeo.model.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroup_Id(Long groupId);

    List<GroupMember> findByUser_Id(Long userId);

    Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);

    // Un membre externe est identifié par son email au sein d'un groupe donné
    // (pas d'identifiant utilisateur puisqu'il n'a pas de compte).
    boolean existsByGroup_IdAndExternalEmail(Long groupId, String externalEmail);
}
