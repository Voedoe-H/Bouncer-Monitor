package Misc

import com.github.tukcps.aadd.AADD
import com.github.tukcps.aadd.DDBuilder

// Standard Numeric Matrix Operations:

/** Matrix Vector Multiplication:
 * @param vec: Input vector v
 * @param mat: Input Matrix A
 * @return vector that is the result of A*v
 * */
fun vecMatMul(vec:DoubleArray,mat:Array<DoubleArray>):DoubleArray
{
    var res = DoubleArray(mat[0].size)
    for(i in mat.indices)
    {
        var sum = 0.0
        for(j in vec.indices)
        {
            sum += mat[i][j]*vec[j]
        }
        res[i] = sum
    }
    return res
}

/** Pair wise addition of two given vectors
 * @param vec1: Input vector a
 * @param vec2: Input vector b
 * @return vector c for which c[i] = a[i]+b[i]
 * */
fun vecAdd(vec1:DoubleArray,vec2:DoubleArray):DoubleArray
{
    var result = DoubleArray(vec1.size)
    for(i in vec1.indices)
    {
        result[i] = vec1[i]+vec2[i]
    }
    return result
}

/** scalar multiplication of a vector
 * @param scalar: scalar s the elements of the vector is multiplied
 * @param vec: the vector a that is multiplied by the scalar
 * @return vector c for which the following holds c[i] = s * a[i]
 * */
fun scalarMul(scalar:Double,vec:DoubleArray):DoubleArray
{
    var res = DoubleArray(vec.size)
    for(j in vec.indices)
    {
        res[j] = scalar * vec[j]

    }
    return res
}

/** Inner product/ Dot product of two vectors in R^n where n is the dimension of both input vectors
 * @param vc1: input vector a
 * @param vc2: input vector b
 * @return value as a result of a dot b
 * */
fun inner(vc1:DoubleArray,vc2:DoubleArray):Double
{
    var res = 0.0
    for(i in vc1.indices)
    {
        res+=vc1[i]*vc2[i]
    }
    return res
}

// Symbolic Matrix Operations:

/** Pair wise addition of two given vectors whose elements are AADDs thus the arithmetic operations are defined over AADDs.
 * For the semantics please refer to "Hierarchical verification of AMS systems with affine arithmetic decision diagrams" by Zivkovic et al.
 * @param vec1: vector a over AADDs
 * @param vec2: vector b over AADDs
 * @return vector c over AADDs s.t. c[i] = a[i] + b[i], the addition is the addition defined over AADDs
 * */
fun vecAddAffine(vec1:Array<AADD>, vec2:Array<AADD>, builder: DDBuilder):Array<AADD>
{
    with(builder)
    {
        var result = Array(vec1.size){scalar(0.0)}
        for(i in vec1.indices)
        {
            result[i] = vec1[i]+vec2[i]
        }
        return result
    }
}

/**
 * scalar multiplication of an scalar AADD with a vector over the AADDs.
 * For the semantics of the multiplication over AADDs please refer to "Hierarchical verification of AMS systems with affine arithmetic decision diagrams" by Zivkovic et al.
 * @param scalar: an AADD representing possible values for the scalar s value
 * @param vec: vector a over the AADDs
 * @return vector b over the AADDs s.t. b[i] = s * a[i], where the multiplication is that defined over AADDs
 * */
fun scalarMulAffine(scalar: AADD, vec:Array<AADD>, builder: DDBuilder):Array<AADD>
{
    with(builder)
    {
        var res = Array(vec.size){scalar(0.0)}
        for(j in vec.indices)
        {
            res[j] = scalar * vec[j]
        }
        return res
    }
}


/**
 * Matrix times vector multiplication. The matrix as well as the vector are defined over AADDs, every value in them is an AADD. For the semantics of Addition and multiplication
 * over AADD please refer to "Hierarchical verification of AMS systems with affine arithmetic decision diagrams" by Zivkovic et al.
 * @param vec: vector b over the AADDs
 * @param mat: matrix A over the AADDs
 * @return a vector c over the AADDs such that c = A * b
 * */
fun vecMatMulAffine(vec:Array<AADD>, mat:Array<Array<AADD>>, builder: DDBuilder):Array<AADD>
{
    with(builder)
    {
        var res = Array<AADD>(mat[0].size){scalar(0.0)}
        for(i in mat.indices)
        {
            var sum = scalar(0.0)
            for(j in vec.indices)
            {
                sum += mat[i][j]*vec[j]
            }
            res[i] = sum
        }
        return res
    }
}