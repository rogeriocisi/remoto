package br.remoto.core.model.config;

/** Linha de MOTORUNIT_TYPE: um tipo de unidade motora de um núcleo. */
public record MotorUnitTypeParams(
        String type,
        String nucleus,
        double twitchTension1,
        double twitchTension2,
        double tetanicTension1,
        double tetanicTension2,
        double tauForce1,
        double tauForce2,
        double ampEmg1,
        double ampEmg2,
        double durEmg1,
        double durEmg2,
        int index) {
}
