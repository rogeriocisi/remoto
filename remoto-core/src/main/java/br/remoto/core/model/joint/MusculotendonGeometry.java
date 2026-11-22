package br.remoto.core.model.joint;

/**
 * Comprimento do musculotendão e braço de momento no tornozelo dos músculos do tríceps sural (SOL,
 * MG, LG) e do tibial anterior (TA), por polinômios de 4º grau no ângulo do tornozelo (graus).
 *
 * <p>Os do MG e do LG dependem do joelho: os coeficientes existem para joelho em 0 e em 90; com
 * outro valor, comprimento e braço ficam 0. Como no original, o ângulo do joelho chega aqui em
 * radianos e é comparado com 0 e com 90, de modo que só o joelho estendido (0) tem coeficientes.
 * Para um músculo desconhecido, os valores anteriores ficam.
 */
public final class MusculotendonGeometry {

    private final String cdMuscle;

    private double length;
    private double momentArm;

    public MusculotendonGeometry(String cdMuscle) {
        this.cdMuscle = cdMuscle;
    }

    /** Atualiza o comprimento (m) para os ângulos do tornozelo (graus) e do joelho. */
    public void updateLength(double ankleAngle, double kneeAngle) {
                
        if(cdMuscle.equals("SOL")){

            length =    9.2739 * Math.pow(10, -11) * Math.pow(ankleAngle, 4) - 
                        3.1478 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) - 
                        2.2431 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                        7.2186 * Math.pow(10, -4) * ankleAngle + 
                        0.32284;
        }
        else if(cdMuscle.equals("MG")){
            if(kneeAngle == 0){
                length =    7.3492 * Math.pow(10, -11) * Math.pow(ankleAngle, 4) - 
                            3.4959 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) - 
                            1.1328 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            7.4794 * Math.pow(10, -4) * ankleAngle + 
                            0.46349;

            }
            else if(kneeAngle == 90){
                length =    7.3209 * Math.pow(10, -11) * Math.pow(ankleAngle, 4) - 
                            3.4982 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) - 
                            1.1266 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            7.4750 * Math.pow(10, -4) * ankleAngle + 
                            0.42343;
            }
            else
                length = 0;
        }
        else if(cdMuscle.equals("LG")){
            if(kneeAngle == 0){
                length =    7.6451 * Math.pow(10, -11) * Math.pow(ankleAngle, 4) - 
                            3.5529 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) - 
                            1.2491 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            7.6234 * Math.pow(10, -4) * ankleAngle + 
                            0.45515;
            }
            else if(kneeAngle == 90){
                length =     7.6452 * Math.pow(10, -11) * Math.pow(ankleAngle, 4) - 
                            3.5540 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) - 
                            1.2478 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            7.6228 * Math.pow(10, -4) * ankleAngle + 
                            0.41375;
            }
            else
                length = 0;
        }
        else if(cdMuscle.equals("TA")){
            length =    1.5002 * Math.pow(10, -10) * Math.pow(ankleAngle, 4) + 
                        2.4220 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) - 
                        1.4106 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) - 
                        7.4414 * Math.pow(10, -4) * ankleAngle + 
                        0.30587;
        }
    }

    /** Atualiza o braço de momento (m) para os ângulos do tornozelo (graus) e do joelho. */
    public void updateMomentArm(double ankleAngle, double kneeAngle) {
        
        if(cdMuscle.equals("SOL")){

            momentArm =    -5.4944 * Math.pow(10, -11) * Math.pow(ankleAngle, 4) - 
                        2.2185 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) + 
                        5.4507 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                        2.5739 * Math.pow(10, -4) * ankleAngle -
                        0.041363;
        }
        else if(cdMuscle.equals("MG")){
            if(kneeAngle == 0){
                momentArm =    -1.0214 * Math.pow(10, -10) * Math.pow(ankleAngle, 4) - 
                            1.8660 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) + 
                            6.0830 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            1.3053 * Math.pow(10, -4) * ankleAngle -
                            0.042864;

        }
        else if(kneeAngle == 90){
                momentArm =    -1.0296 * Math.pow(10, -10) * Math.pow(ankleAngle, 4) - 
                            1.8619 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) + 
                            6.0872 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            1.2988 * Math.pow(10, -4) * ankleAngle - 
                            0.042841;
        }
        else
                momentArm = 0;
        }
        else if(cdMuscle.equals("LG")){
            if(kneeAngle == 0){
                momentArm =    -1.0234 * Math.pow(10, -10) * Math.pow(ankleAngle, 4) - 
                            1.9345 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) + 
                            6.1803 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            1.4388 * Math.pow(10, -4) * ankleAngle - 
                            0.043687;
            }
            else if(kneeAngle == 90){
                momentArm = -1.0261 * Math.pow(10, -10) * Math.pow(ankleAngle, 4) - 
                            1.9351 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) + 
                            6.1825 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                            1.4374 * Math.pow(10, -4) * ankleAngle - 
                            0.043684;
            }
            else
                momentArm = 0;
        }
        else if(cdMuscle.equals("TA")){
            momentArm =    -4.3391 * Math.pow(10, -10) * Math.pow(ankleAngle, 4) - 
                        4.4462 * Math.pow(10, -8) * Math.pow(ankleAngle, 3) - 
                        3.8880 * Math.pow(10, -6) * Math.pow(ankleAngle, 2) + 
                        1.6568 * Math.pow(10, -4) * ankleAngle + 
                        0.042616;
        }
    }

    public String cdMuscle() {
        return cdMuscle;
    }

    public double length() {
        return length;
    }

    public double momentArm() {
        return momentArm;
    }
}
