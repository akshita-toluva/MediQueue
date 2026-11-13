package com.mediqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "patient_health_profiles")
@Getter @Setter @NoArgsConstructor   // not @Data: its equals/hashCode would walk the lazy patient + collections
public class PatientHealthProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true -> the DATABASE guarantees one profile per patient, even if two saves race
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, unique = true)
    private User patient;

    // @ElementCollection = a child table of plain values (no entity, no id of its own).
    // Set, not List: no duplicates, and Hibernate can EAGER-fetch two Sets (two Lists -> MultipleBagFetchException)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "patient_conditions", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)   // store "DIABETES", not 0 -> reordering the enum can't corrupt data
    @Column(name = "condition_code", nullable = false, length = 40)
    private Set<MedicalCondition> conditions = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "patient_food_allergies", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "allergy_code", nullable = false, length = 40)
    private Set<FoodAllergy> foodAllergies = new HashSet<>();

    // Set in the SERVICE, not with @PreUpdate: changing only a collection does not
    // mark this row dirty, so @PreUpdate would silently not fire.
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}