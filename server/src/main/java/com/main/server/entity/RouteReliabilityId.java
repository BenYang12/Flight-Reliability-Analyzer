package com.main.server.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// RouteReliabilityId.java = composite primary key of route-reliability record
// I'm thinking my reliability record is uniquely identified by these four values together -> {carrierIata, origin, dest, depHour}
// RouteReliabilityId supplied the identity

// The primary key of route_reliability is four columns, so it needs its own class. 
// @Embeddable = "This class does not get its own database table. Its fields can be placed inside another entity's table"
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RouteReliabilityId implements Serializable {
    private String carrierIata;
    private String origin;
    private String dest;
    private Integer depHour; // 0-23
}
