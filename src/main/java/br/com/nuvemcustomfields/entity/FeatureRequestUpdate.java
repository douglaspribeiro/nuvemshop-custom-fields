package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="feature_request_updates")
public class FeatureRequestUpdate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="request_id",nullable=false)
    @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
    private FeatureRequest request;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private FeatureRequestStatus status;
    @Column(nullable=false,length=4000) private String message="";
    @Column(name="submission_token",nullable=false,length=36,unique=true) private String submissionToken;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @Column(name="email_state",nullable=false,length=20) private String emailState;
    @Column(name="email_sent_at") private Instant emailSentAt;
    public Long getId(){return id;}
    public FeatureRequest getRequest(){return request;} public void setRequest(FeatureRequest v){request=v;}
    public FeatureRequestStatus getStatus(){return status;} public void setStatus(FeatureRequestStatus v){status=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getSubmissionToken(){return submissionToken;} public void setSubmissionToken(String v){submissionToken=v;}
    public Instant getCreatedAt(){return createdAt;}
    public String getEmailState(){return emailState;} public void setEmailState(String v){emailState=v;}
    public Instant getEmailSentAt(){return emailSentAt;} public void setEmailSentAt(Instant v){emailSentAt=v;}
}
