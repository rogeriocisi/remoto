package br.remoto.core.simulation.setup;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import br.remoto.core.model.config.*;
import br.remoto.core.simulation.BiomechanicalInput;
import br.remoto.core.simulation.Current;
import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.util.Point;

/**
 * Monta a {@link Configuration} do ReMoto original a partir dos parâmetros tipados
 * ({@link ModelConfig}, lidos das tabelas do cenário pelo {@link LegacyTablesReader}).
 *
 * <p>É a leitura do {@code ConfigurationDAO} original sem o banco: cada método {@code load*}
 * percorre as linhas de uma tabela, na ordem do {@code ORDER BY} da consulta original, e preenche
 * os mesmos objetos com os mesmos valores. No fim vêm os padrões que o DAO fixava (SOCDS, tornozelo
 * isométrico, fuso de Mileusnic, OTG de Crago, ganhos do fuso, distribuição predefinida das
 * unidades motoras), que o campo {@code settings} do cenário pode trocar depois.
 */
public final class ConfigurationAssembler
{
	private final ModelConfig source;
	private Configuration conf;

	private ConfigurationAssembler(ModelConfig source)
	{
		this.source = source;
	}

	public static Configuration assemble(ModelConfig params)
	{
		return new ConfigurationAssembler(params).assemble(params.id());
	}

	private Configuration assemble(int idConfiguration)
	{
		conf = new Configuration();

		conf.setJoints( loadJoints() );
    	conf.setNuclei( loadNucleus() );
    	conf.setCategories( loadCategories() );
    	conf.setGeneral( loadConfiguration(idConfiguration) );
    	conf.setNerves( loadNerves(idConfiguration) );
    	conf.setDescendingCommands( loadDescendingCommands(idConfiguration) );
    	conf.setInjectedCurrents( loadInjectedCurrents(idConfiguration) );
    	conf.setSynapseTypes( loadSynapseTypes(idConfiguration) );
    	conf.setMarcovTypes( loadMarcovTypes(idConfiguration) );
    	conf.setDynamicTypes( loadDynamicTypes(idConfiguration) );
    	conf.setNeuronTypes( loadNeuronTypes(idConfiguration) );
    	conf.setMotorunitsTypes( loadMotorunitsTypes(idConfiguration) );
    	conf.setMiscellaneous( loadMiscellaneous(idConfiguration) );
    	conf.setMuPresetPosition( loadMuPresetPosition() );

    	conf.setBiomechanicalInputs(loadBiomechanicalInputs(idConfiguration));
    	conf.setMuscles(loadMuscles(idConfiguration));

    	loadInterfaceParameters(idConfiguration);

    	conf.setCdJoint("ankle");
    	conf.setCdMuscleModel(ReMoto.SOCDS);
    	conf.setCdJointModel("isometric");
    	conf.setCdSpindleModel(ReMoto.spindleModelMileusnic);
    	conf.setCdGtoModel(ReMoto.lumpedGtoModelCrago);
    	conf.setCdEMGModel(ReMoto.emgModelHermiteRodriguez);

        conf.setPrimaryBag1Gain(7000);
        conf.setPrimaryBag2AndChainGain(3800);
        conf.setSecondaryBag2AndChainGain(3000);

    	conf.setDecimationFrequency(20000);

    	conf.setMuDistribution(ReMoto.muDistributionPreset);
    	conf.setRecruitmentOrderFES("linear");

    	conf.setInitialRecruitmentThresholdIa(0);
    	conf.setFinalRecruitmentThresholdIa(50);
    	conf.setInitialRecruitmentThresholdII(0);
    	conf.setFinalRecruitmentThresholdII(50);
    	conf.setInitialRecruitmentThresholdIb(0);
    	conf.setFinalRecruitmentThresholdIb(50);

    	conf.setpercentageOfMVC(0.15);

    	return conf;
	}

    private GeneralVO loadConfiguration(int idConfiguration)
	{
		GeneralVO general = new GeneralVO();

    	try
    	{
        	Rows rs = Rows.of(source.simulation(), (SimulationParams r) -> new Object[] {
        			r.id(), r.name(), r.description(), r.tFin(), r.step(), r.merge(), r.user(), r.keepProperties(), r.storeResults(), r.storeSignals() });

        	if( rs.next() )
        	{
        		general.setId( rs.getInt(1) );
        		general.setName( rs.getString(2) );
        		general.setDescription( rs.getString(3) );
        		general.setTFin( rs.getDouble(4) );
        		general.setStep( rs.getDouble(5) );
        		general.setMerge( rs.getBoolean(6) );
        		general.setCdUser( rs.getString(7) );
        		general.setKeepProperties( rs.getBoolean(8) );
        		general.setStoreResults( rs.getBoolean(9) );
        		general.setStoreSignals( rs.getBoolean(10) );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return general;
	}

    private List loadJoints()
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.joints(), (JointParams r) -> new Object[] {
        			r.cd(), r.name(), r.numNuclei(), r.index() });

        	while( rs.next() )
        	{
            	JointVO vo = new JointVO();

            	vo.setCd( rs.getString(1) );
            	vo.setName( rs.getString(2) );
            	vo.setNumNuclei( rs.getInt(3) );
            	vo.setInd( rs.getInt(4) );

        		list.add( vo );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadNucleus()
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.nuclei(), (NucleusParams r) -> new Object[] {
        			r.cd(), r.name(), r.layer(), r.index(), r.joint() });

        	while( rs.next() )
        	{
            	Nucleus nucleus = new Nucleus();

            	nucleus.setCd( rs.getString(1) );
            	nucleus.setName( rs.getString(2) );
            	nucleus.setLayer( rs.getString(3) );
            	nucleus.setInd( rs.getInt(4) );
            	nucleus.setJoint( rs.getString(5) );

        		list.add( nucleus );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadCategories()
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.categories(), (CategoryParams r) -> new Object[] {
        			r.cd(), r.name() });

        	while( rs.next() )
        	{
            	Category category = new Category();

            	category.setCdCategory( rs.getString(1) );
            	category.setName( rs.getString(2) );

        		list.add( category );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

	private List loadInjectedCurrents(int idConfiguration)
	{
		List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.injectedCurrents(), (InjectedCurrentParams r) -> new Object[] {
        			r.imax(), r.nucleus(), r.neuronType(), r.signal(), r.tIni(), r.tFin(), r.amp(), r.freq(), r.width(), r.compartment(), r.active(), r.index(), r.delay() });

        	while( rs.next() )
        	{
        		Current inj = new Current();

        		inj.setImax( rs.getDouble(1) );
        		inj.setCdNucleus( rs.getString(2) );
        		inj.setCdNeuronType( rs.getString(3) );

				inj.setCdSignal( rs.getString(4) );
				inj.setIni( rs.getDouble(5) );
				inj.setFin( rs.getDouble(6) );
				inj.setAmp( rs.getDouble(7) );
				inj.setFreq( rs.getDouble(8) );
				inj.setWidth( rs.getDouble(9) );

        		inj.setCompartment( rs.getString(10) );
        		inj.setActive( rs.getBoolean(11) );
        		inj.setIndex( rs.getInt(12) );
        		inj.setDelay( rs.getDouble(13));
        		inj.setModFreq( 1);
        		inj.setModFactor( 1);

        		ModulatingSignal signal = new ModulatingSignal();

        		signal.setCdSignal(inj.getCdSignal());
        		signal.setTini(inj.getIni());
        		signal.setTfin(inj.getFin());
        		signal.setAmp(inj.getAmp());
        		signal.setFreq(inj.getFreq());
        		signal.setWidth(inj.getWidth());
        		signal.setDelay(inj.getDelay());
        		signal.setModFreq(inj.getModFreq());
        		signal.setModFactor(inj.getModFactor());

        		inj.setSignal(signal);

        		list.add( inj );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

	private List loadDescendingCommands(int idConfiguration)
	{
		List list = new ArrayList();

        try
    	{
        	Rows rs = Rows.of(source.descendingCommands(), (DescendingCommandParams r) -> new Object[] {
        			r.signal(), r.nucleus(), r.neuronType(), r.modType(), r.tIni(), r.tFin(), r.amp(), r.freq(), r.width(), r.delay() });

        	while( rs.next() )
        	{
        		ModulatingSignal signal = new ModulatingSignal();

        		signal.setCdSignal( rs.getString(1) );
        		signal.setCdNucleus( rs.getString(2) );
        		signal.setCdNeuronType( rs.getString(3) );
        		signal.setModType( rs.getString(4) );
        		signal.setTini( rs.getDouble(5) );
        		signal.setTfin( rs.getDouble(6) );
        		signal.setAmp( rs.getDouble(7) );
        		signal.setFreq( rs.getDouble(8) );
        		signal.setWidth( rs.getDouble(9) );
        		signal.setDelay( rs.getDouble(10) );
        		signal.setModFreq( 1);
        		signal.setModFactor( 0.5);

        		list.add( signal );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadSynapseTypes(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.synapses(), (SynapseParams r) -> new Object[] {
        			r.conductanceType(), r.nucleus(), r.nucleusPre(), r.e(), r.gmax(), r.connectivity(), r.compartment(), r.active(), r.delay(), r.index() });

        	while( rs.next() )
        	{
            	ConductanceVO g = new ConductanceVO();

            	g.setCdConductanceType( rs.getString(1) );
            	g.setCdNucleus( rs.getString(2) );
            	g.setCdNucleusPre( rs.getString(3) );
            	g.setE( rs.getDouble(4) );
            	g.setGmax( rs.getDouble(5) );
            	g.setConnectivity( rs.getDouble(6) );
            	g.setCompartment( rs.getString(7) );
            	g.setActive( rs.getBoolean(8) );
            	g.setDelay( rs.getDouble(9) );
            	g.setIndex( rs.getInt(10) );

        		list.add( g );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private Hashtable loadMarcovTypes(int idConfiguration)
	{
    	Hashtable hash = new Hashtable();

    	try
    	{
        	Rows rs = Rows.of(source.ionicChannels(), (IonicChannelParams r) -> new Object[] {
        			r.type(), r.nucleus(), r.e(), r.gmax(), r.tpeak(), r.tmax(), r.alpha(), r.beta(), r.index() });

        	while( rs.next() )
        	{
        		ConductanceVO g = new ConductanceVO();

            	g.setCdConductanceType( rs.getString(1) );
            	g.setCdNucleus( rs.getString(2) );
            	g.setE( rs.getDouble(3) );
            	g.setGmax( rs.getDouble(4) );
            	g.setTpeak( rs.getDouble(5) );
            	g.setTmax( rs.getDouble(6) );
            	g.setAlpha( rs.getDouble(7) );
            	g.setBeta( rs.getDouble(8) );
            	g.setIndex( rs.getInt(9) );

        		hash.put( g.getCdNucleus() + g.getCdConductanceType() + g.getPolarity(), g);
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return hash;
	}

    private List loadDynamicTypes(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.synapticDynamics(), (SynapticDynamicsParams r) -> new Object[] {
        			r.nucleus(), r.nucleusPre(), r.conductanceType(), r.dynamicType(), r.tau(), r.variation() });

        	while( rs.next() )
        	{
        		DynamicVO vo = new DynamicVO();

        		vo.setCdNucleus( rs.getString(1) );
        		vo.setCdNucleusPre( rs.getString(2) );
        		vo.setCdConductanceType( rs.getString(3) );
        		vo.setDynamicType( rs.getString(4) );
        		vo.setTau( rs.getDouble(5) );
        		vo.setVariation( rs.getDouble(6) );

        		list.add( vo );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadNeuronTypes(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.neuronTypes(), (NeuronTypeParams r) -> new Object[] {
        			r.type(), r.category(), r.nucleus(), r.threshold1(), r.threshold2(), r.cm(), r.ri(), r.rmDend1(), r.rmDend2(), r.rmSoma1(), r.rmSoma2(), r.dDend1(), r.dDend2(), r.lDend1(), r.lDend2(), r.dSoma1(), r.dSoma2(), r.lSoma1(), r.lSoma2(), r.totalArea(), r.distribution(), r.mean(), r.std(), r.axonVelocity1(), r.axonVelocity2(), r.axonThreshold1(), r.axonThreshold2(), r.quantity(), r.nerve(), r.active(), r.index(), r.thresholdCa1(), r.thresholdCa2() });

        	while( rs.next() )
        	{
        		NeuronVO neu = new NeuronVO();

        		neu.setType( rs.getString(1) );
        		neu.setCategory( rs.getString(2) );
        		neu.setCdNucleus( rs.getString(3) );
        		neu.setThreshold1( rs.getDouble(4) );
        		neu.setThreshold2( rs.getDouble(5) );
        		neu.setCm( rs.getDouble(6) );
        		neu.setRi( rs.getDouble(7) );
        		neu.setRmDend1( rs.getDouble(8) );
        		neu.setRmDend2( rs.getDouble(9) );
        		neu.setRmSoma1( rs.getDouble(10) );
        		neu.setRmSoma2( rs.getDouble(11) );
    			neu.setDdend1( rs.getDouble(12) );
    			neu.setDdend2( rs.getDouble(13) );
    			neu.setLdend1( rs.getDouble(14) );
    			neu.setLdend2( rs.getDouble(15) );
    			neu.setDsoma1( rs.getDouble(16) );
    			neu.setDsoma2( rs.getDouble(17) );
        		neu.setLsoma1( rs.getDouble(18) );
        		neu.setLsoma2( rs.getDouble(19) );
        		neu.setTotalArea( rs.getDouble(20) );

    			neu.setDistribution( rs.getString(21) );
    			neu.setMean( rs.getDouble(22) );
    			neu.setOrder( (int) rs.getDouble(23) );
    			neu.setAxonVelocity1( rs.getDouble(24) );
    			neu.setAxonVelocity2( rs.getDouble(25) );
    			neu.setAxonThreshold1( rs.getDouble(26) );
    			neu.setAxonThreshold2( rs.getDouble(27) );

    			neu.setQuantity( rs.getInt(28) );
    			neu.setCdNerve( rs.getString(29) );
    			neu.setActive( rs.getBoolean(30) );
    			neu.setIndex( rs.getInt(31) );
    			neu.setThresholdCa1( rs.getDouble(32) );
    			neu.setThresholdCa2( rs.getDouble(33) );

    			ModulatingSignal signal = conf.getDescendingCommand( neu.getType(), neu.getCdNucleus() );

    			if( signal != null )
    			{
        			neu.setCdSignal( signal.getCdSignal() );
        			neu.setAmp( signal.getAmp() );
        			neu.setFreq( signal.getFreq() );
        			neu.setIni( signal.getTini() );
        			neu.setFin( signal.getTfin() );
        			neu.setModType( signal.getModType() );
        			neu.setWidth( signal.getWidth() );
        			neu.setModFreq( signal.getModFreq() );
        			neu.setModFactor( signal.getModFactor() );
    			}

    			list.add( neu );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadMotorunitsTypes(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.motorUnitTypes(), (MotorUnitTypeParams r) -> new Object[] {
        			r.type(), r.nucleus(), r.twitchTension1(), r.twitchTension2(), r.tetanicTension1(), r.tetanicTension2(), r.tauForce1(), r.tauForce2(), r.ampEmg1(), r.ampEmg2(), r.durEmg1(), r.durEmg2() });

        	while( rs.next() )
        	{
            	MotorUnitVO mu = new MotorUnitVO();

            	mu.setType( rs.getString(1) );
            	mu.setCdNucleus( rs.getString(2) );
            	mu.setTwitchTension1( rs.getDouble(3) );
            	mu.setTwitchTension2( rs.getDouble(4) );
            	mu.setMaxTension1( rs.getDouble(5) );
            	mu.setMaxTension2( rs.getDouble(6) );
        		mu.setContractionTime1( rs.getDouble(7) );
        		mu.setContractionTime2( rs.getDouble(8) );
        		mu.setAmpEMG1( rs.getDouble(9) );
        		mu.setAmpEMG2( rs.getDouble(10) );
        		mu.setLambdaEMG1( rs.getDouble(11) );
        		mu.setLambdaEMG2( rs.getDouble(12) );

        		mu.setTwitchTension1Raikova(rs.getDouble(3));
        		mu.setTwitchTension2Raikova(rs.getDouble(4));
    			mu.setContractionTime1Raikova(rs.getDouble(7));
        		mu.setContractionTime2Raikova(rs.getDouble(8));

        		if(mu.getCdNucleus().equals(ReMoto.SOL)){
                    if(mu.getType().equals("S")){
                        mu.setHalfRelaxationTimeRaikova1(310);
                        mu.setHalfRelaxationTimeRaikova2(260);
                        mu.setb1(0.7);
                        mu.setb2(0.5);
                        mu.settwTet1(2.97);
                        mu.settwTet2(4.08);
                        mu.setbRaikova1(1.5);
                        mu.setbRaikova2(1);
                        mu.settwTetRaikova1(1.5746);
                        mu.settwTetRaikova2(2.164);
                    }
                    else if(mu.getType().equals("FR")){
                        mu.setHalfRelaxationTimeRaikova1(245);
                        mu.setHalfRelaxationTimeRaikova2(180);
                        mu.setb1(0.5);
                        mu.setb2(0.3);
                        mu.settwTet1(4.08);
                        mu.settwTet2(6.71);
                        mu.setbRaikova1(1);
                        mu.setbRaikova2(0.7);
                        mu.settwTetRaikova1(2.164);
                        mu.settwTetRaikova2(2.973);
                    }
                    else if(mu.getType().equals("FF")){
                        mu.setContractionTime1Raikova(96);
                        mu.setContractionTime2Raikova(84);
                        mu.setHalfRelaxationTimeRaikova1(170);
                        mu.setHalfRelaxationTimeRaikova2(125);
                        mu.setb1(0.3);
                        mu.setb2(0.23);
                        mu.settwTet1(6.71);
                        mu.settwTet2(8.73);
                        mu.setbRaikova1(0.7);
                        mu.setbRaikova2(0.69);
                        mu.settwTetRaikova1(2.973);
                        mu.settwTetRaikova2(3.013);
                    }
                }
                else if(mu.getCdNucleus().equals(ReMoto.TA)) {
                    if(mu.getType().equals("S")){
                        mu.setHalfRelaxationTimeRaikova1(202);
                        mu.setHalfRelaxationTimeRaikova2(182);
                        mu.setb1(1);
                        mu.setb2(0.85);
                        mu.settwTet1(2.16);
                        mu.settwTet2(2.49);
                        mu.setbRaikova1(1.3);
                        mu.setbRaikova2(1.25);
                        mu.settwTetRaikova1(1.75);
                        mu.settwTetRaikova2(1.8);
                    }
                    else if(mu.getType().equals("FR")){
                        mu.setHalfRelaxationTimeRaikova1(133.5);
                        mu.setHalfRelaxationTimeRaikova2(95.5);
                        mu.setb1(0.85);
                        mu.setb2(0.48);
                        mu.settwTet1(2.49);
                        mu.settwTet2(4.25);
                        mu.setbRaikova1(1.25);
                        mu.setbRaikova2(0.5);
                        mu.settwTetRaikova1(1.8);
                        mu.settwTetRaikova2(4.08);
                    }
                    else if(mu.getType().equals("FF")){
                        mu.setContractionTime1Raikova(55);
                        mu.setContractionTime2Raikova(25);
                        mu.setHalfRelaxationTimeRaikova1(115);
                        mu.setHalfRelaxationTimeRaikova2(65);
                        mu.setb1(0.48);
                        mu.setb2(0.43);
                        mu.settwTet1(4.25);
                        mu.settwTet2(4.72);

                        mu.setbRaikova1(0.25);
                        mu.setbRaikova2(0.25);
                        mu.settwTetRaikova1(8.0386);
                        mu.settwTetRaikova2(8.0386);
                    }
                }
                else{
                    if(mu.getType().equals("S")){
                        mu.setHalfRelaxationTimeRaikova1(202);
                        mu.setHalfRelaxationTimeRaikova2(182);
                        mu.setb1(1);
                        mu.setb2(0.85);
                        mu.settwTet1(2.16);
                        mu.settwTet2(2.49);
                        mu.setbRaikova1(2);
                        mu.setbRaikova2(1.7);
                        mu.settwTetRaikova1(1.313);
                        mu.settwTetRaikova2(1.447);
                    }
                    else if(mu.getType().equals("FR")){
                        mu.setHalfRelaxationTimeRaikova1(133.5);
                        mu.setHalfRelaxationTimeRaikova2(95.5);
                        mu.setb1(0.85);
                        mu.setb2(0.48);
                        mu.settwTet1(2.49);
                        mu.settwTet2(4.25);
                        mu.setbRaikova1(1.7);
                        mu.setbRaikova2(1.1);
                        mu.settwTetRaikova1(1.447);
                        mu.settwTetRaikova2(1.998);
                    }
                    else if(mu.getType().equals("FF")){
                        mu.setContractionTime1Raikova(55);
                        mu.setContractionTime2Raikova(25);
                        mu.setHalfRelaxationTimeRaikova1(115);
                        mu.setHalfRelaxationTimeRaikova2(65);
                        mu.setb1(0.48);
                        mu.setb2(0.43);
                        mu.settwTet1(4.25);
                        mu.settwTet2(4.72);
                        mu.setbRaikova1(1.1);
                        mu.setbRaikova2(1);
                        mu.settwTetRaikova1(1.998);
                        mu.settwTetRaikova2(2.164);
                    }
                }

        		list.add( mu );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadNerves(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.nerves(), (NerveParams r) -> new Object[] {
        			r.cd(), r.joint(), r.active(), r.stimulusSpinalEntry(), r.stimulusEndPlate(), r.index(), r.start(), r.stop(), r.peak(), r.frequency(), r.signal(), r.tIni(), r.tFin(), r.amp(), r.freq(), r.width(), r.delay() });

        	while( rs.next() )
        	{
        		NerveVO nerve = new NerveVO();

            	nerve.setCdNerve( rs.getString(1) );
            	nerve.setCdJoint( rs.getString(2) );
        		nerve.setActive( rs.getBoolean(3) );
        		nerve.setStimulusSpinalEntry( rs.getDouble(4) );
        		nerve.setStimulusEndPlate( rs.getDouble(5) );
        		nerve.setIndex( rs.getInt(6) );

        		nerve.setTini( rs.getDouble(7) );
        		nerve.setTfin( rs.getDouble(8) );
        		nerve.setAmp( rs.getDouble(9) );
        		nerve.setFreq( rs.getDouble(10) );

        		nerve.setCdSignal( rs.getString(11) );
        		nerve.setModulating_tini( rs.getDouble(12) );
        		nerve.setModulating_tfin( rs.getDouble(13) );
        		nerve.setModulating_amp( rs.getDouble(14) );
        		nerve.setModulating_freq( rs.getDouble(15) );
        		nerve.setWidth( rs.getDouble(16) );
        		nerve.setDelay( rs.getDouble(17));
        		nerve.setModFreq( 1);
        		nerve.setModFactor(0.5);

        		ModulatingSignal signal = new ModulatingSignal();

        		signal.setCdSignal(nerve.getCdSignal());
        		signal.setTini(nerve.getTini());
        		signal.setTfin(nerve.getTfin());
        		signal.setAmp(nerve.getAmp());
        		signal.setFreq(nerve.getFreq());
        		signal.setWidth(nerve.getWidth());
        		signal.setDelay(nerve.getDelay());
        		signal.setModFreq(nerve.getModFreq());
        		signal.setModFactor(nerve.getModFactor());

        		nerve.setSignal(signal);

        		list.add( nerve );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadMuscles(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.muscles(), (HillMuscleParams r) -> new Object[] {
        			r.muscle(), r.optimalLength(), r.viscosityCoefficient(), r.elasticCoefParallel(), r.cT(), r.kT(), r.lrT(), r.pennationAngle(), r.slackTendonLength(), r.maximumMuscleForce(), r.muscleMass(), r.index() });

        	while( rs.next() )
        	{
        		MuscleVO vo = new MuscleVO();

        		vo.setCdMuscle(rs.getString(1));
        		vo.setOptimalLength(rs.getDouble(2));
        		vo.setViscosityCoeficient(rs.getDouble(3));
        		vo.setElasticCoeficientOfParallelElement(rs.getDouble(4));
        		vo.setC_T(rs.getDouble(5));
        		vo.setK_T(rs.getDouble(6));
        		vo.setLR_T(rs.getDouble(7));
        		vo.setPennationAngle(rs.getDouble(8));
        		vo.setSlackTendonLength(rs.getDouble(9));
        		vo.setMaximumMuscleForce(rs.getDouble(10));
        		vo.setMuscleMass(rs.getDouble(11));
        		vo.setIndex(rs.getInt(12));

        		list.add( vo );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadBiomechanicalInputs(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.injectedCurrents(), (InjectedCurrentParams r) -> new Object[] {
        			r.imax(), r.nucleus(), r.neuronType(), r.signal(), r.tIni(), r.tFin(), r.amp(), r.freq(), r.width(), r.compartment(), r.active(), r.index(), r.delay() });

        	while( rs.next() )
        	{
        		BiomechanicalInput biomechanicalInput = new BiomechanicalInput();

        		biomechanicalInput.setImax( rs.getDouble(1) );
        		biomechanicalInput.setCdNucleus( rs.getString(2) );
        		biomechanicalInput.setCdNeuronType( rs.getString(3) );

        		biomechanicalInput.setCdSignal( rs.getString(4) );
        		biomechanicalInput.setIni( rs.getDouble(5) );
        		biomechanicalInput.setFin( rs.getDouble(6) );
        		biomechanicalInput.setAmp( rs.getDouble(7) );
        		biomechanicalInput.setFreq( rs.getDouble(8) );
        		biomechanicalInput.setWidth( rs.getDouble(9) );

        		biomechanicalInput.setCompartment( rs.getString(10) );
        		biomechanicalInput.setActive( rs.getBoolean(11) );
        		biomechanicalInput.setIndex( rs.getInt(12) );
        		biomechanicalInput.setDelay( rs.getDouble(13));
        		biomechanicalInput.setModFreq( 1);
        		biomechanicalInput.setModFactor( 0.5);

        		ModulatingSignal signal = new ModulatingSignal();

        		signal.setCdSignal(biomechanicalInput.getCdSignal());
        		signal.setTini(biomechanicalInput.getIni());
        		signal.setTfin(biomechanicalInput.getFin());
        		signal.setAmp(biomechanicalInput.getAmp());
        		signal.setFreq(biomechanicalInput.getFreq());
        		signal.setWidth(biomechanicalInput.getWidth());
        		signal.setDelay(biomechanicalInput.getDelay());
        		signal.setModFreq(biomechanicalInput.getModFreq());
        		signal.setModFactor(biomechanicalInput.getModFactor());

        		biomechanicalInput.setSignal(signal);

        		list.add( biomechanicalInput );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private List loadMiscellaneous(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.miscellaneous(), (MiscellaneousParam r) -> new Object[] {
        			r.property(), r.description(), r.value(), r.index(), r.division() });

        	while( rs.next() )
        	{
        		MiscellaneousVO vo = new MiscellaneousVO();

        		vo.setProperty( rs.getString(1) );
        		vo.setDescription( rs.getString(2) );
        		vo.setValue( rs.getDouble(3) );
        		vo.setIndex( rs.getInt(4) );
        		vo.setDivision( rs.getBoolean(5) );

        		list.add( vo );
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return list;
	}

    private Hashtable loadMuPresetPosition()
	{
    	Hashtable hash = new Hashtable();

    	try
    	{
        	Rows rs = Rows.of(source.motorUnitPositions(), (MotorUnitPosition r) -> new Object[] {
        			r.nucleus(), r.mu(), r.y(), r.z() });

        	while( rs.next() )
        	{
        		Point point = new Point();

        		String cdNucleus = rs.getString(1);
        		String cd = rs.getString(2);

        		point.setY( rs.getDouble(3) );
        		point.setZ( rs.getDouble(4) );

        		hash.put( cdNucleus + cd, point);
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}

    	return hash;
	}

    private void loadInterfaceParameters(int idConfiguration)
	{
    	List list = new ArrayList();

    	try
    	{
        	Rows rs = Rows.of(source.interfaces(), (InterfaceParams r) -> new Object[] {
        			r.nucleus() });

        	while( rs.next() )
        	{
            	conf.setCdNucleus(rs.getString(1));
        	}

        	rs.close();
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.getMessage());
    	}
	}
}
