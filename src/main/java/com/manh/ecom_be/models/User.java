package com.manh.ecom_be.models;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE users SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class User extends BaseEntity implements UserDetails, OAuth2User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fullname", length = 100)
    private String fullName;

    @Column(name = "phone_number", length = 10, nullable = true)
    private String phoneNumber;

    @Column(name = "email", length = 255, nullable = true)
    private String email;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(name = "password", length = 200, nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "date_of_birth")
    private Date dateOfBirth;

    @Column(name = "facebook_account_id")
    private String facebookAccountId;

    @Column(name = "google_account_id")
    private String googleAccountId;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private com.manh.ecom_be.models.Role role;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorityList = new ArrayList<>();
        authorityList.add(new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()));
        return authorityList;
    }

    @Override
    public String getUsername() {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            return phoneNumber;

        } else if (email != null && !email.isEmpty()) {
            return email;
        }
        return "";
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() {return true;}
    @Override public boolean isCredentialsNonExpired() {return true;}
    @Override public boolean isEnabled() {return active;}

    @Override public Map<String, Object> getAttributes() {
        return new HashMap<String, Object>();
    }

    @Override public String getName() {
        return getAttribute("name");
    }

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<Favorite> favorites = new ArrayList<>();
}
