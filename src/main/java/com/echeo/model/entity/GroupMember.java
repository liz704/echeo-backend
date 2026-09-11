package com.echeo.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Membre d'un groupe. Deux cas possibles, jamais mélangés (voir contrainte
 * SQL chk_group_member_identity) :
 * - Membre INSCRIT : user pointe vers un compte ÉCHÉO existant.
 * - Membre EXTERNE : pas de compte ; seuls externalFullName/externalEmail
 *   (et éventuellement externalPhone) sont renseignés. C'est le cas normal
 *   pour la plupart des membres d'une cotisation — avoir un compte ÉCHÉO
 *   n'est pas requis pour recevoir des rappels de paiement par email.
 */
@Entity
@Table(name = "group_members", uniqueConstraints = {
        @UniqueConstraint(name = "uq_group_member", columnNames = {"group_id", "user_id"})
})
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    @JsonIgnoreProperties({"members", "events"})
    private Group group;

    // Nullable : absent si membre externe (voir externalFullName/externalEmail).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"passwordHash"})
    private User user;

    @Column(name = "external_full_name")
    private String externalFullName;

    @Column(name = "external_email")
    private String externalEmail;

    @Column(name = "external_phone")
    private String externalPhone;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "groupMember", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EventMemberStatus> eventStatuses = new ArrayList<>();

    public GroupMember() {
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = OffsetDateTime.now();
        }
    }

    /**
     * Nom d'affichage, que le membre soit inscrit ou externe.
     */
    public String getContactFullName() {
        return user != null ? user.getFullName() : externalFullName;
    }

    /**
     * Email de contact, que le membre soit inscrit ou externe — c'est ce
     * qui est utilisé pour envoyer les relances et reçus par email.
     */
    public String getContactEmail() {
        return user != null ? user.getEmail() : externalEmail;
    }

    /**
     * Téléphone de contact si disponible (aucun des deux cas ne le garantit).
     */
    public String getContactPhone() {
        return user != null ? user.getPhone() : externalPhone;
    }

    public boolean isRegisteredUser() {
        return user != null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getExternalFullName() {
        return externalFullName;
    }

    public void setExternalFullName(String externalFullName) {
        this.externalFullName = externalFullName;
    }

    public String getExternalEmail() {
        return externalEmail;
    }

    public void setExternalEmail(String externalEmail) {
        this.externalEmail = externalEmail;
    }

    public String getExternalPhone() {
        return externalPhone;
    }

    public void setExternalPhone(String externalPhone) {
        this.externalPhone = externalPhone;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public List<EventMemberStatus> getEventStatuses() {
        return eventStatuses;
    }

    public void setEventStatuses(List<EventMemberStatus> eventStatuses) {
        this.eventStatuses = eventStatuses;
    }
}
