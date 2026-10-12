package com.bnnn.polyfield.map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** Invisible, unbreakable ground for the Polyfield map; 1-8 layers of 1/8 block like snow. */
public class ColliderBlock extends Block {
    public static final IntProperty LAYERS = Properties.LAYERS;
    private static final VoxelShape[] SHAPES = new VoxelShape[9];

    static {
        for (int i = 1; i <= 8; i++) {
            SHAPES[i] = Block.createCuboidShape(0, 0, 0, 16, i * 2, 16);
        }
    }

    public ColliderBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(LAYERS, 8));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(LAYERS)];
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }
}
