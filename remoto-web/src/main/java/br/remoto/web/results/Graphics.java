package br.remoto.web.results;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Os gráficos da tela de resultados do site antigo ({@code ResultsAction.setFormProperties}), com
 * os mesmos códigos, nomes, títulos e eixos, na ordem dos menus de lá.
 */
public final class Graphics {

    /** Como os pontos são desenhados. */
    public enum Kind { LINE, POINTS, HISTOGRAM, TABLE }

    /**
     * @param code   código do gráfico na tela antiga ({@code cdGraphicType})
     * @param label  nome no menu
     * @param title  título do gráfico (o nome do elemento é acrescentado, como lá)
     * @param xLabel eixo x
     * @param yLabel eixo y
     */
    public record Graphic(String code, String label, String title, String xLabel, String yLabel, Kind kind) {
    }

    /** Elementos neurais do menu "Neural element", com os gráficos de cada um. */
    public static final Map<String, List<Graphic>> NEURAL = new LinkedHashMap<>();
    /** Gráficos do tipo "Biomechanical Elements". */
    public static final List<Graphic> BIOMECHANICAL;

    private static final String TIME = "Time [ms]";

    static {
        Graphic spikeTimes = g("spikeTimes", "Spike Times", "Spike times", TIME, "Index", Kind.POINTS);
        Graphic firingRate = g("firingRate", "Instantaneous Firing Rate (IFR)", "Instantaneous firing rate", TIME,
                "IFR [Hz]", Kind.POINTS);
        Graphic isi = g("ISI", "Interspike Interval (ISI)", "Interspike interval (ISI)", TIME,
                "Interspike interval (ISI) [ms]", Kind.POINTS);
        Graphic histogram = g("histogram", "ISI Histogram", "Histogram", "ISI [ms]", "Number of elements in each bin",
                Kind.HISTOGRAM);
        Graphic soma = g("somaPotential", "Soma potential", "Soma potential", TIME, "Membrane potential [mV]", Kind.LINE);
        Graphic dendrite = g("dendritePotential", "Dendrite potential", "Dendrite potential", TIME,
                "Membrane potential [mV]", Kind.LINE);
        Graphic conductance = g("conductance", "Conductance", "Conductance", TIME, "Conductance [nS]", Kind.LINE);
        Graphic injected = g("injectedCurrent", "Injected Current", "Injected Current", TIME, "Current [nA]", Kind.LINE);
        Graphic recruitment = g("recruitmentThresholds", "Recruitment Threshold", "Thresholds", "Index",
                "Threshold [mV]", Kind.POINTS);
        Graphic velocity = g("axonConductionVelocity", "Axon Conduction Velocity", "Axon Conduction Velocity", "Index",
                "Velocity [m/s]", Kind.POINTS);
        Graphic resistance = g("somaticInputResistance", "Somatic Input Resistance", "Somatic Input Resistance", "Index",
                "Resistance [Mohm]", Kind.POINTS);
        Graphic rheobase = g("rheobase", "Rheobase", "Rheobase", "Index", "Rheobase [nA]", Kind.POINTS);
        Graphic axonThresholds = g("axonThresholds", "Axon Thresholds", "Axon Threshold", "Index", "Threshold [mA]",
                Kind.POINTS);
        Graphic positions = g("neuronPositions", "Neuron Positions", "Neuron position", "Index",
                "Position (caudal-rostral)", Kind.POINTS);
        Graphic phase = g("VsxVdot", "Vs x Vdot", "Vs x Vdot", "Vs [mV]", "dV/dt [mV/s]", Kind.POINTS);
        Graphic stimulusRate = g("stimulusRate", "Normalized Stimulus Rate", "Normalized stimulus rate (CT/ISI)", TIME,
                "Stimulus rate", Kind.POINTS);
        Graphic stimulusForce = g("stimulusRateVsForce", "Stimulus Rate VS Force", "Stimulus rate VS Force",
                "Stimulus rate", "Force [N]", Kind.POINTS);
        Graphic thresholdConductance = g("thresholdVsInputConductance", "Threshold VS Input Conductance",
                "Threshold VS Input Conductance", "Input conductance [uS]", "Threshold [mV]", Kind.POINTS);

        NEURAL.put("mns", List.of(spikeTimes, firingRate, isi, histogram, soma, dendrite, conductance, injected,
                recruitment, velocity, resistance, rheobase, axonThresholds, positions, phase, stimulusRate,
                stimulusForce, thresholdConductance));
        NEURAL.put("afs", List.of(
                g("meanFiringRate", "Mean Firing Rate", "Mean Firing Rate", TIME, "Firing rate [Hz]", Kind.LINE),
                spikeTimes, firingRate, isi, histogram, velocity, axonThresholds,
                g("recruitmentThresholds", "Recruitment Threshold", "Thresholds", "Index", "Threshold", Kind.POINTS)));
        NEURAL.put("dts", List.of(
                g("meanISI", "Mean ISI", "ISI mean value", TIME, "ISI [ms]", Kind.LINE),
                firingRate, spikeTimes, isi, histogram));
        NEURAL.put("ins", List.of(soma, g("conductance", "Conductance", "Conductance", TIME, "Conductance [nS]",
                Kind.LINE), injected, spikeTimes, firingRate, isi, histogram, recruitment, resistance, rheobase,
                thresholdConductance));

        BIOMECHANICAL = List.of(
                g("muscleLength", "Muscle Length", "Muscle length", TIME, "Length [m]", Kind.LINE),
                g("muscleVelocity", "Muscle Velocity", "Muscle velocity", TIME, "Velocity [m/s]", Kind.LINE),
                g("muscleAcceleration", "Muscle Acceleration", "Muscle acceleration", TIME, "Acceleration [m/s^2]",
                        Kind.LINE),
                g("activationNormSType", "Muscle Activation - S Type", "Muscle activation - S-type", TIME,
                        "Activation [normalized]", Kind.LINE),
                g("activationNormFType", "Muscle Activation - F Type", "Muscle activation - F-type", TIME,
                        "Activation [normalized]", Kind.LINE),
                g("activationNorm", "Total Muscle Activation", "Total muscle activation", TIME,
                        "Activation [normalized]", Kind.LINE),
                g("twitchPeak", "Twitch Peak", "Motor-unit twitch peak", "Index", "Peak amplitude [N]", Kind.POINTS),
                g("contractionTime", "Contraction Time", "Motor-unit contraction time", "Index", "Contraction time [ms]",
                        Kind.POINTS),
                g("halfRelaxation", "Half-relaxation time", "Motor-unit half-relaxation time", "Index",
                        "Half-relaxation time [ms]", Kind.POINTS),
                g("amplitudeMUAP", "MUAP Amplitude", "Amplitude of motor-unit action potential", "Index",
                        "MUAP amplitude [mV]", Kind.POINTS),
                g("muscleLengthNorm", "Muscle Normalized Length", "Muscle length", TIME, "Length [normalized]",
                        Kind.LINE),
                g("forceParallelElement", "Muscle Parallel Element Force", "Muscle force (parallel element)", TIME,
                        "Force [normalized]", Kind.LINE),
                g("forceViscousElement", "Muscle Viscous Element Force", "Muscle force (viscous element)", TIME,
                        "Force [normalized]", Kind.LINE),
                g("forceActiveSType", "Muscle S Type Active Force", "Muscle force (active S-type)", TIME,
                        "Force [normalized]", Kind.LINE),
                g("forceActiveFType", "Muscle F Type Active Force", "Muscle force (active F-type)", TIME,
                        "Force [normalized]", Kind.LINE),
                g("muscleForce", "Muscle Force", "Muscle force", TIME, "Force [N]", Kind.LINE),
                g("tendonLength", "Tendon Length", "Tendon length", TIME, "Length [m]", Kind.LINE),
                g("tendonForce", "Tendon Force", "Tendon force", TIME, "Force [N]", Kind.LINE),
                g("normalizedForce", "Normalized Musculotendon Force", "Normalized musculotendon force", TIME,
                        "Force [% MVC]", Kind.LINE),
                g("EMG", "EMG", "EMG", TIME, "EMG [mV]", Kind.LINE),
                g("jointAngle", "Joint Angle", "Joint angle", TIME, "Angle [deg]", Kind.LINE),
                g("jointVelocity", "Joint Angular Velocity", "Angular Velocity", TIME, "Angular Velocity [deg/s]",
                        Kind.LINE),
                g("jointCenterMass", "Center of Mass", "Center of Mass", TIME, "CoM [mm]", Kind.LINE),
                g("jointCenterPressure", "Center of Pressure", "Center of Pressure", TIME, "CoP [mm]", Kind.LINE),
                g("jointTorque", "Joint Torque", "Joint torque", TIME, "Torque [Nm]", Kind.LINE),
                g("jointMuscleTorque", "Joint Muscle Torque", "Joint muscle torque", TIME, "Torque [Nm]", Kind.LINE),
                g("jointGravTorque", "Gravitational Torque", "Gravitational torque", TIME, "Torque [Nm]", Kind.LINE),
                g("jointPassiveTorque", "Passive Joint Torque", "Passive joint torque", TIME, "Torque [Nm]", Kind.LINE),
                g("jointAngleVsJointTorque", "Joint Angle VS Joint Torque", "Joint angle VS joint torque",
                        "Angle [deg]", "Torque [Nm]", Kind.POINTS),
                g("jointAngleVsVelocity", "Joint Angle VS Angular Velocity", "Joint angle VS angular velocity",
                        "Angle [deg]", "Angular Velocity [deg/s]", Kind.POINTS),
                g("jointDisturbance", "Disturbance", "Disturbance", TIME, "Torque [N.m]", Kind.LINE),
                g("MUsPosition", "Motor Unit Position", "MU position within muscle cross-section", "Width [mm]",
                        "Depth [mm]", Kind.POINTS),
                g("musculotendonLength", "Musculotendon Length", "Musculotendon length", TIME, "Length [m]", Kind.LINE),
                g("musculotendonMomentArm", "Musculotendon Moment Arm", "Musculotendon moment arm", TIME,
                        "Moment arm [m]", Kind.LINE),
                g("MTLengthVSTendonForce", "MT Length VS Tendon Force", "MT length VS tendon force", "Length [m]",
                        "Force [N]", Kind.POINTS),
                g("tendonLengthVSTendonForce", "Tendon Length VS Tendon Force", "Tendon length VS tendon force",
                        "Length [m]", "Force [N]", Kind.POINTS),
                g("muscleLengthVSTendonForce", "Muscle Length VS Tendon Force", "Muscle length VS tendon force",
                        "Length [m]", "Force [N]", Kind.POINTS),
                g("pennationAngle", "Pennation angle", "Pennation angle", TIME, "Pennation angle [deg]", Kind.LINE));
    }

    private Graphics() {
    }

    private static Graphic g(String code, String label, String title, String x, String y, Kind kind) {
        return new Graphic(code, label, title, x, y, kind);
    }

    public static Optional<Graphic> neural(String element, String code) {
        return NEURAL.getOrDefault(element, List.of()).stream().filter(g -> g.code().equals(code)).findFirst();
    }

    public static Optional<Graphic> biomechanical(String code) {
        return BIOMECHANICAL.stream().filter(g -> g.code().equals(code)).findFirst();
    }

    /** As condutâncias da tela antiga ({@code cdSpecification} do gráfico "Conductance"). */
    public static final List<String> CONDUCTANCES = List.of("gNa", "gKf", "gKs", "gCa", "gNap", "gExc-dend",
            "gInib-dend", "gExc-soma", "gInib-soma");

    /** Condutâncias que só os motoneurônios têm. {@code gNap}, só com a membrana contínua. */
    public static final List<String> MOTONEURON_ONLY = List.of("gCa", "gNap");
}
