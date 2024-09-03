
import BouncerMonitor.*
import SymbolicSimulation.*
import NumericSimulation.*
import com.github.tukcps.aadd.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

fun main(args: Array<String>) {
    sigmaDeltaBenchmark()
}

/**
 * Complete example that presents the workflow of the anomaly detection method presented in the paper
 * "Reliable and Real-Time Anomaly Detection for Safety-Relevant Systems" by Heermann et al.
 * */
fun sigmaDeltaBenchmark()
{
    // First the context object that manages all the AADD context  needs to be created
    val builder = DDBuilder()
    with(builder)
    {


        //-------------------------- Step 1: Transformation of the Model, hybrid automata, into decision tree form --------------------------
        // First we do our symbolic simulation for one step to parse our hybrid automata model into a representation by AADDs and BDD^As for a bounded region
        val symbolicResults = sigmaDeltaSymbolicSimulation(this)

        // Some prep work for the monitor generation
        val preState = mutableMapOf<String,DD<*>>()
        for(cont in symbolicResults.prestateAffineForms)
        {
            preState[cont.key] = cont.value
        }
        for(disc in symbolicResults.preStateDisc)
        {
            preState[disc.key] = disc.value
        }

        val postState = mutableMapOf<String,DD<*>>()
        for(cont in symbolicResults.postState)
        {
            postState[cont.key] = cont.value
        }
        for(disc in symbolicResults.postStateDisc)
        {
            postState[disc.key] = disc.value
        }

        // -------------------------- Step 2: Actual monitor generation, entails transformation of the AADD and BDD^A results from Step 1 into an AACDD. --------------------------
        // With this representation we can create the monitor that does the evaluation that is presented in the paper.
        val delta = 0.001
        val bouncerMonitor = MinimalBouncerMonitor(this,preState,postState,delta)

        // -------------------------- Step 3: Runtime Phase where all the data is evaluated --------------------------
        val C_T = datasetCGeneration()
        // Transforming the trajectories to transitions
        val C = transformDataSetIntoSetOfTransitions(C_T) //
        val SPE_T = datasetSPEGeneration()
        val SPE = transformDataSetIntoSetOfTransitions(SPE_T)
        val LPE_T = datasetLPEGeneration()
        val LPE = transformDataSetIntoSetOfTransitions(LPE_T)
        // Data set N is excluded here as it was generated using a python script

        // Evaluation of data set C
        var numberInliersC = 0
        var numberOutliersC = 0

        for(transition in C)
        {
            // Actual Anomaly Detection / Runtime Verification against system model
            val verdict = bouncerMonitor.evaluateTransitionOnlyContinuous(transition)
            if(verdict)numberInliersC++
            else numberOutliersC++
        }

        println("---- C Data Set Results: -----")
        println("Number Inliers C: $numberInliersC")
        println("Number Outliers C: $numberOutliersC")
        println("------------------------------")

        // Evaluation of data set SPE
        var numberInliersSPE = 0
        var numberOutliersSPE = 0

        for(transition in SPE)
        {
            val verdict = bouncerMonitor.evaluateTransitionOnlyContinuous(transition)
            if(verdict)numberInliersSPE++
            else numberOutliersSPE++
        }

        println("----- SPE Data Set Results: -----")
        println("Number Inliers SPE: $numberInliersSPE")
        println("Number Outliers SPE: $numberOutliersSPE")
        println("---------------------------------")

        //Evaluation of data set LPE
        var numberInliersLPE = 0
        var numberOutliersLPE = 0

        for(transition in LPE)
        {
            val verdict = bouncerMonitor.evaluateTransitionOnlyContinuous(transition)
            if(verdict)numberInliersLPE++
            else numberOutliersLPE++
        }

        println("----- LPE Data Set Results: -----")
        println("Number Inliers LPE: $numberInliersLPE")
        println("Number Outliers LPE: $numberOutliersLPE")
        println("---------------------------------")
    }
}

/**
 * Quick little data class that just holds the generated trajectories of the sigma-delta modulator
 * */
data class DataSet(val x1trajectories : MutableList<MutableList<Double>>,
                   val x2Trajectories : MutableList<MutableList<Double>>,
                   val x3Trajectories : MutableList<MutableList<Double>>)

/**
 * The generation function for the data set C presented in "Reliable and Real-Time Anomaly Detection for Safety-Relevant Systems" by Heermann et al.
 * Instead of writing it into the csv files as it was done for the actual experiments here it is just kept in memory.
 * */
fun datasetCGeneration() : DataSet
{
    val numberOfTrajectories = 1000
    val numberOfTransitionsPerTrajectory = 100

    val x1Trajectories = mutableListOf<MutableList<Double>>()
    val x2Trajectories = mutableListOf<MutableList<Double>>()
    val x3Trajectories = mutableListOf<MutableList<Double>>()
    val dTrajectories = mutableListOf<MutableList<Boolean>>()

    for(i in 0..<numberOfTrajectories)
    {
        val x1Trajectory = mutableListOf<Double>()
        val x2Trajectory = mutableListOf<Double>()
        val x3Trajectory = mutableListOf<Double>()
        val dTrajectory = mutableListOf<Boolean>()

        val x1Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x2Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x3Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val uInit = ThreadLocalRandom.current().nextDouble(-0.5 , 0.5)
        var dInit = true

        if((i%2)==0)
        {
            dInit = false
        }

        val _a1_b1 = Random.nextDouble(0.0444-0.01 , 0.0444+0.01)
        val _a2_b2 = Random.nextDouble(0.2881-0.1 , 0.2881+0.1)
        val _a3_b3 = Random.nextDouble(0.7997-0.1 , 0.7997+0.1)


        val numRes = sigmaDeltaNumericSimulation(x1Init,x2Init,x3Init,uInit,numberOfTransitionsPerTrajectory,dInit,properties(a1 = _a1_b1, a2 = _a2_b2, a3 = _a3_b3, b1 = _a1_b1, b2 = _a2_b2, b3=_a3_b3))

        for(a in numRes.x1Trajectory){x1Trajectory.add(a)}
        for(a in numRes.x2Trajectory){x2Trajectory.add(a)}
        for(a in numRes.x3Trajectory){x3Trajectory.add(a)}
        for(a in numRes.dTrajecory)
        {
            if(a){dTrajectory.add(a)}
            else{dTrajectory.add(a)}
        }
        x1Trajectories.add(x1Trajectory)
        x2Trajectories.add(x2Trajectory)
        x3Trajectories.add(x3Trajectory)
        dTrajectories.add(dTrajectory)
    }
    return DataSet(x1Trajectories,x2Trajectories,x3Trajectories)
}

/**
 * The generation function for the data set SPE presented in "Reliable and Real-Time Anomaly Detection for Safety-Relevant Systems" by Heermann et al.
 * Instead of writing it into the csv files as it was done for the actual experiments here it is just kept in memory.
 * */
fun datasetSPEGeneration() : DataSet
{
    val numberOfTrajectories = 1000
    val numberOfTransitionsPerTrajectory = 100

    val x1Trajectories = mutableListOf<MutableList<Double>>()
    val x2Trajectories = mutableListOf<MutableList<Double>>()
    val x3Trajectories = mutableListOf<MutableList<Double>>()
    val dTrajectories = mutableListOf<MutableList<Boolean>>()

    // Generate Upper Half Property Errors
    for(i in 0..<numberOfTrajectories/2)
    {
        val x1Trajectory = mutableListOf<Double>()
        val x2Trajectory = mutableListOf<Double>()
        val x3Trajectory = mutableListOf<Double>()
        val dTrajectory = mutableListOf<Boolean>()

        val x1Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x2Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x3Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val uInit = ThreadLocalRandom.current().nextDouble(-0.5 , 0.5)//-0.5 .. 0.5
        var dInit = true

        if((i%2)==0)
        {
            dInit = false
        }

        val _a1_b1 = Random.nextDouble(0.0545 , 0.06)
        val _a2_b2 = Random.nextDouble(0.3882 , 0.4)
        val _a3_b3 = Random.nextDouble(0.8997 , 0.9)


        val numRes = sigmaDeltaNumericSimulation(x1Init,x2Init,x3Init,uInit,numberOfTransitionsPerTrajectory,dInit,properties(a1 = _a1_b1, a2 = _a2_b2, a3 = _a3_b3, b1 = _a1_b1, b2 = _a2_b2, b3=_a3_b3))

        for(a in numRes.x1Trajectory){x1Trajectory.add(a)}
        for(a in numRes.x2Trajectory){x2Trajectory.add(a)}
        for(a in numRes.x3Trajectory){x3Trajectory.add(a)}
        for(a in numRes.dTrajecory)
        {
            if(a){dTrajectory.add(a)}
            else{dTrajectory.add(a)}
        }
        x1Trajectories.add(x1Trajectory)
        x2Trajectories.add(x2Trajectory)
        x3Trajectories.add(x3Trajectory)
        dTrajectories.add(dTrajectory)
    }

    // Generate Lower Half Property Errors
    for(i in numberOfTrajectories/2..<numberOfTrajectories)
    {
        val x1Trajectory = mutableListOf<Double>()
        val x2Trajectory = mutableListOf<Double>()
        val x3Trajectory = mutableListOf<Double>()
        val dTrajectory = mutableListOf<Boolean>()

        val x1Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x2Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x3Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val uInit = ThreadLocalRandom.current().nextDouble(-0.5 , 0.5)
        var dInit = true

        if((i%2)==0)
        {
            dInit = false
        }

        val _a1_b1 = Random.nextDouble(0.02 , 0.0343)
        val _a2_b2 = Random.nextDouble(0.1 , 0.188)
        val _a3_b3 = Random.nextDouble(0.6 , 0.6997)

        val numRes = sigmaDeltaNumericSimulation(x1Init,x2Init,x3Init,uInit,numberOfTransitionsPerTrajectory,dInit,properties(a1 = _a1_b1, a2 = _a2_b2, a3 = _a3_b3, b1 = _a1_b1, b2 = _a2_b2, b3=_a3_b3))

        for(a in numRes.x1Trajectory){x1Trajectory.add(a)}
        for(a in numRes.x2Trajectory){x2Trajectory.add(a)}
        for(a in numRes.x3Trajectory){x3Trajectory.add(a)}
        for(a in numRes.dTrajecory)
        {
            if(a){dTrajectory.add(a)}
            else{dTrajectory.add(a)}
        }
        x1Trajectories.add(x1Trajectory)
        x2Trajectories.add(x2Trajectory)
        x3Trajectories.add(x3Trajectory)
        dTrajectories.add(dTrajectory)
    }
    return DataSet(x1Trajectories,x2Trajectories,x3Trajectories)
}

/**
 * The generation function for the data set LPE presented in "Reliable and Real-Time Anomaly Detection for Safety-Relevant Systems" by Heermann et al.
 * Instead of writing it into the csv files as it was done for the actual experiments here it is just kept in memory.
 * */
fun datasetLPEGeneration() : DataSet
{
    val numberOfTrajectories = 1000
    val numberOfTransitionsPerTrajectory = 100

    val x1Trajectories = mutableListOf<MutableList<Double>>()
    val x2Trajectories = mutableListOf<MutableList<Double>>()
    val x3Trajectories = mutableListOf<MutableList<Double>>()
    val dTrajectories = mutableListOf<MutableList<Boolean>>()

    // Generate Upper Half Property Errors
    for(i in 0..<numberOfTrajectories/2)
    {
        val x1Trajectory = mutableListOf<Double>()
        val x2Trajectory = mutableListOf<Double>()
        val x3Trajectory = mutableListOf<Double>()
        val dTrajectory = mutableListOf<Boolean>()

        val x1Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x2Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x3Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val uInit = ThreadLocalRandom.current().nextDouble(-0.5 , 0.5)
        var dInit = true

        if((i%2)==0)
        {
            dInit = false
        }

        val _a1_b1 = Random.nextDouble(0.0545 , 0.5)
        val _a2_b2 = Random.nextDouble(0.3882 , 0.7)
        val _a3_b3 = Random.nextDouble(0.8997 , 1.6)


        val numRes = sigmaDeltaNumericSimulation(x1Init,x2Init,x3Init,uInit,numberOfTransitionsPerTrajectory,dInit,properties(a1 = _a1_b1, a2 = _a2_b2, a3 = _a3_b3, b1 = _a1_b1, b2 = _a2_b2, b3=_a3_b3))

        for(a in numRes.x1Trajectory){x1Trajectory.add(a)}
        for(a in numRes.x2Trajectory){x2Trajectory.add(a)}
        for(a in numRes.x3Trajectory){x3Trajectory.add(a)}
        for(a in numRes.dTrajecory)
        {
            if(a){dTrajectory.add(a)}
            else{dTrajectory.add(a)}
        }
        x1Trajectories.add(x1Trajectory)
        x2Trajectories.add(x2Trajectory)
        x3Trajectories.add(x3Trajectory)
        dTrajectories.add(dTrajectory)
    }

    // Generate Lower Half Property Errors
    for(i in numberOfTrajectories/2..<numberOfTrajectories)
    {
        val x1Trajectory = mutableListOf<Double>()
        val x2Trajectory = mutableListOf<Double>()
        val x3Trajectory = mutableListOf<Double>()
        val dTrajectory = mutableListOf<Boolean>()

        val x1Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x2Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val x3Init = ThreadLocalRandom.current().nextDouble(-0.01,0.01)
        val uInit = ThreadLocalRandom.current().nextDouble(-0.5 , 0.5)
        var dInit = true

        if((i%2)==0)
        {
            dInit = false
        }

        val _a1_b1 = Random.nextDouble(0.001 , 0.0343)
        val _a2_b2 = Random.nextDouble(0.01 , 0.188)
        val _a3_b3 = Random.nextDouble(0.01 , 0.6997)


        val numRes = sigmaDeltaNumericSimulation(x1Init,x2Init,x3Init,uInit,numberOfTransitionsPerTrajectory,dInit,properties(a1 = _a1_b1, a2 = _a2_b2, a3 = _a3_b3, b1 = _a1_b1, b2 = _a2_b2, b3=_a3_b3))

        for(a in numRes.x1Trajectory){x1Trajectory.add(a)}
        for(a in numRes.x2Trajectory){x2Trajectory.add(a)}
        for(a in numRes.x3Trajectory){x3Trajectory.add(a)}
        for(a in numRes.dTrajecory)
        {
            if(a){dTrajectory.add(a)}
            else{dTrajectory.add(a)}
        }
        x1Trajectories.add(x1Trajectory)
        x2Trajectories.add(x2Trajectory)
        x3Trajectories.add(x3Trajectory)
        dTrajectories.add(dTrajectory)
    }
    return DataSet(x1Trajectories,x2Trajectories,x3Trajectories)
}

/**
 * As the name suggests this function transforms the trajectories from the data set into single transitions.
 * @param dataSet: in this case either the C,SPE or LPE data set
 * @return A list of transitions, a transition here is a map that contains the two consecutive double values for the three internal continuous variables where the string is the identifier
 * */
fun transformDataSetIntoSetOfTransitions(dataSet: DataSet) : MutableList<MutableMap<String,Pair<Double,Double>>>
{
    val transitions = mutableListOf<MutableMap<String,Pair<Double,Double>>>()
    for((i,t) in dataSet.x1trajectories.withIndex())
    {
        for(j in 1..<t.size)
        {
            val transition = mutableMapOf<String,Pair<Double,Double>>()
            val x1Pair = Pair(dataSet.x1trajectories[i][j-1],dataSet.x1trajectories[i][j])
            val x2pair = Pair(dataSet.x2Trajectories[i][j-1],dataSet.x2Trajectories[i][j])
            val x3Pair = Pair(dataSet.x3Trajectories[i][j-1],dataSet.x3Trajectories[i][j])
            transition["x0"] = x1Pair
            transition["x1"] = x2pair
            transition["x2"] = x3Pair
            transitions.add(transition)
        }
    }
    return transitions
}