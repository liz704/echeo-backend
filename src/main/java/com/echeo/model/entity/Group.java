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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Groupe créé par un utilisateur (owner) réunissant plusieurs membres
 * autour d'événements de cotisation/paiement collectif.
 * Nom de classe "Group" mappé explicitement sur la table "groups"
 * (GROUP étant un mot réservé SQL).
 */
@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnoreProperties({"passwordHash"})
    private User owner;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Le propriétaire peut choisir de ne PAS recevoir de copie de chaque
    // relance envoyée à ses membres (pour éviter la surcharge de notifications).
    @Column(name = "notify_owner_on_reminders", nullable = false)
    private boolean notifyOwnerOnReminders = true;

    // La suppression backend d'un groupe doit être réelle : on cascade
    // au niveau JPA en plus du ON DELETE CASCADE en base, pour que
    // toute manipulation via l'EntityManager reste cohérente.
    @JsonIgnore
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GroupMember> members = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GroupEvent> events = new ArrayList<>();

    public Group() {
    }

    public Group(Long id, String name, String description, User owner, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.createdAt = createdAt;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isNotifyOwnerOnReminders() {
        return notifyOwnerOnReminders;
    }

    public void setNotifyOwnerOnReminders(boolean notifyOwnerOnReminders) {
        this.notifyOwnerOnReminders = notifyOwnerOnReminders;
    }

    public List<GroupMember> getMembers() {
        return members;
    }

    public void setMembers(List<GroupMember> members) {
        this.members = members;
    }

    public List<GroupEvent> getEvents() {
        return events;
    }

    public void setEvents(List<GroupEvent> events) {
        this.events = events;
    }
}
