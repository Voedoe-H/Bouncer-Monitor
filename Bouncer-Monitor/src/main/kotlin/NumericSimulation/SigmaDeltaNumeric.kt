package NumericSimulation
import Misc.*

/**
 * Data class holding all the results from the interior states of the numeric sigma-delta modulator simulation.
 * */
data class numericRestuls(val x1Trajectory: MutableList<Double>,
                          val x2Trajectory: MutableList<Double>,
                          val x3Trajectory: MutableList<Double>,
                          val dTrajecory: MutableList<Boolean>)

/**
 * Data class holding all the specific property values for the numeric sigma-delta modulator simulation
 * */
data class properties(val a1 : Double = 0.0444,
                      val a2 : Double = 0.2881,
                      val a3 : Double = 0.7997,
                      val b1 : Double = 0.0444,
                      val b2 : Double = 0.2881,
                      val b3 : Double = 0.7997)

/**
 * Numeric simulation of a sigma-delta modulator of third order. This simulation is a derivative of the discrete hybrid automata model presented in the paper
 * "Verification of Analog and Mixed-Signal Circuits Using Hybrid System Techniques" by Dang et al. . The property names also correspond.
 * The idea is that first the continuous state evolves followed by an evolution of the discrete state.
 * @param _x0: The initial state of the first integrator in the sigma-delta modulator usually 0
 * @param _x1: The initial state of the second integrator in the sigma-delta modulator usually 0
 * @param _x2: The initial state of the third integrator in the sigma-delta modulator usually 0
 * @param _u: In this specific simulation we only take a constant input into consideration
 * @param _iterations: Value for simulated time
 * @param _pos: The initial starting discrete state of the simulation, this correlates to the output bit of the sigma-delta modulator
 * @return data class object that holds the trajectories of all the internal states of the sigma-delta modulator over the simulated time
 * */
fun sigmaDeltaNumericSimulation(_x0:Double,_x1:Double,_x2:Double,_u:Double,_iterations:Int,_pos:Boolean,prop: properties):numericRestuls
{
    // Prep Work
    val x0Trace = mutableListOf<Double>()
    val x1Trace = mutableListOf<Double>()
    val x2Trace = mutableListOf<Double>()
    val dTrace = mutableListOf<Boolean>()

    val iterations = _iterations

    val a1 = prop.a1
    val a2 = prop.a2
    val a3 = prop.a3

    val b1 = prop.b1 // 0.0444
    val b2 = prop.b2
    val b3 = prop.b3

    var x0 = _x0
    var x1 = _x1
    var x2 = _x2

    var pos = _pos
    var neg = !pos

    val matAPos = arrayOf(
        arrayOf(1.0, 0.0, 0.0).toDoubleArray(),
        arrayOf(1.0, 1.0, 0.0).toDoubleArray(),
        arrayOf(0.0, 1.0, 1.0).toDoubleArray()
    )
    val matBPos = arrayOf(b1, b2, b3)
    val constPos = arrayOf(-a1,- a2, -a3)

    val matANeg = arrayOf(
        arrayOf(1.0, 0.0, 0.0).toDoubleArray(),
        arrayOf(1.0, 1.0, 0.0).toDoubleArray(),
        arrayOf(0.0, 1.0, 1.0).toDoubleArray()
    )
    val matBNeg = arrayOf(b1, b2, b3)
    val constNeg = arrayOf(a1, a2, a3)

    val u = _u
    x0Trace.add(x0)
    x1Trace.add(x1)
    x2Trace.add(x2)
    dTrace.add(pos)

    // Start of the Simulation Loop
    for (i in 0..<iterations) {

        // State A
        if (pos and neg.not()) {
            val Axp = vecMatMul(arrayOf(x0, x1, x2).toDoubleArray(), matAPos)
            val bup = scalarMul(u, matBPos.toDoubleArray())
            val sump = vecAdd(vecAdd(Axp, bup), constPos.toDoubleArray())
            x0 = sump[0]
            x1 = sump[1]
            x2 = sump[2]
        }
        // State B
        else {
            val Axn = vecMatMul(arrayOf(x0, x1, x2).toDoubleArray(), matANeg)
            val bun = scalarMul(u, matBNeg.toDoubleArray())
            val sumn = vecAdd(vecAdd(Axn, bun), constNeg.toDoubleArray())
            x0 = sumn[0]
            x1 = sumn[1]
            x2 = sumn[2]
        }

        val condinp = (x2 + u)
        // State A -> B
        if (pos.and(neg.not())) {
            if ( condinp < 0.0) {
                pos = false
                neg = true
            }
        }
        // State B -> A
        else {
            if (condinp >= 0.0) {
                pos = true
                neg = false
            }
        }
        x0Trace.add(x0)
        x1Trace.add(x1)
        x2Trace.add(x2)
        dTrace.add(pos)
    }

    return numericRestuls(x0Trace,x1Trace,x2Trace,dTrace)
}