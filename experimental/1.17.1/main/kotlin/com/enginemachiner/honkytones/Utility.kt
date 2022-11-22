import kotlin.reflect.KClass

private fun getProjectileOnBlock(world: World, pos: BlockPos): Entity? {

    val state = world.getBlockState(pos)

    val box = state.getOutlineShape(world, pos).boundingBox.offset(pos)
    val list = world.getNonSpectatingEntities( PersistentProjectileEntity::class.java, box )
    for (entity in list) {
        val b = entity is PersistentProjectileEntity
        if (b && entity.owner != null) return entity.owner
    }

    return null

}

object RedstoneTracerData {

    val triggerBlocks = mutableSetOf<KClass<out Block>>(
        AbstractButtonBlock::class,     LeverBlock::class
    )

    val traceBlocks = mutableSetOf(
        MusicPlayerBlock::class,    RedstoneWireBlock::class
    )

    init { traceBlocks.addAll( triggerBlocks ) }

    fun isBlockOnFilter(block: Block, filter: MutableSet<KClass<out Block>>): Boolean {
        var b = false
        for ( blockClass in filter ) {
            b = b || blockClass.isInstance(block)
        }
        return b
    }

}

fun cleanTrace( world: World, circuit: MutableSet<BlockPos> ) {

    // Clean if the circuit changed
    var b1 = false
    val filter = RedstoneTracerData.traceBlocks
    for ( blockPos in circuit ) {
        val block = world.getBlockState(blockPos).block
        if ( !RedstoneTracerData.isBlockOnFilter(block, filter) ) { b1 = true; break }
    }

    if (b1) circuit.clear()

}

fun readBlockTrace( world: World, circuit: MutableSet<BlockPos>, pos: BlockPos ): Entity? {

    cleanTrace(world, circuit)
    if ( circuit.isEmpty() ) writeBlockTrace(world, pos, circuit)

    val filter = RedstoneTracerData.triggerBlocks
    for ( storedPos in circuit ) {
        val newBlock = world.getBlockState(storedPos).block
        if ( RedstoneTracerData.isBlockOnFilter(newBlock, filter) ) {
            return getProjectileOnBlock(world, storedPos)
        }
    }

    return null

}

fun writeBlockTrace(world: World, pos: BlockPos, circuit: MutableSet<BlockPos>) {
    writeBlockTrace(world, pos, circuit, RedstoneTracerData.traceBlocks)
}

private fun writeBlockTrace( world: World, pos: BlockPos, circuit: MutableSet<BlockPos>,
                             filter: MutableSet<KClass<out Block>> ) {

    val currentBlock = world.getBlockState(pos).block
    val formerFilter = RedstoneTracerData.traceBlocks
    val nextFilter = RedstoneTracerData.triggerBlocks

    val b = RedstoneTracerData.isBlockOnFilter(currentBlock, formerFilter)
    if ( !circuit.contains(pos) && b ) circuit.add(pos)

    val positions = mutableListOf(
        pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()
    )

    for ( newPos in positions ) {
        val newBlock = world.getBlockState(newPos).block
        val b = RedstoneTracerData.isBlockOnFilter(newBlock, formerFilter)
                && !circuit.contains(newPos)
        if (b) { circuit.add(newPos); writeBlockTrace(world, newPos, circuit) }
        else if (filter != nextFilter) writeBlockTrace(world, newPos, circuit, nextFilter)
    }

}