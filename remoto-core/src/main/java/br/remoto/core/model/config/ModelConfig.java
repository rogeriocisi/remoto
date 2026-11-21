package br.remoto.core.model.config;

import java.util.List;

/**
 * Os parâmetros de um modelo do ReMoto, tipados, uma lista por tabela do banco original.
 *
 * <p>Cada lista vem na ordem em que o {@code ConfigurationDAO} original lia a tabela (o
 * {@code ORDER BY} da consulta dele, com os empates na ordem da chave primária). A ordem importa:
 * ela decide a ordem de criação dos neurônios e, com isso, a dos sorteios.
 *
 * @param id configuração (ID_CONFIGURATION)
 */
public record ModelConfig(
        int id,
        List<SimulationParams> simulation,
        List<JointParams> joints,
        List<NucleusParams> nuclei,
        List<CategoryParams> categories,
        List<NerveParams> nerves,
        List<DescendingCommandParams> descendingCommands,
        List<InjectedCurrentParams> injectedCurrents,
        List<SynapseParams> synapses,
        List<IonicChannelParams> ionicChannels,
        List<SynapticDynamicsParams> synapticDynamics,
        List<NeuronTypeParams> neuronTypes,
        List<MotorUnitTypeParams> motorUnitTypes,
        List<MiscellaneousParam> miscellaneous,
        List<MotorUnitPosition> motorUnitPositions,
        List<HillMuscleParams> muscles,
        List<InterfaceParams> interfaces) {

    public ModelConfig {
        simulation = List.copyOf(simulation);
        joints = List.copyOf(joints);
        nuclei = List.copyOf(nuclei);
        categories = List.copyOf(categories);
        nerves = List.copyOf(nerves);
        descendingCommands = List.copyOf(descendingCommands);
        injectedCurrents = List.copyOf(injectedCurrents);
        synapses = List.copyOf(synapses);
        ionicChannels = List.copyOf(ionicChannels);
        synapticDynamics = List.copyOf(synapticDynamics);
        neuronTypes = List.copyOf(neuronTypes);
        motorUnitTypes = List.copyOf(motorUnitTypes);
        miscellaneous = List.copyOf(miscellaneous);
        motorUnitPositions = List.copyOf(motorUnitPositions);
        muscles = List.copyOf(muscles);
        interfaces = List.copyOf(interfaces);
    }
}
