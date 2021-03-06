package com.oitsjustjose.geolosys.common.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.oitsjustjose.geolosys.Geolosys;
import com.oitsjustjose.geolosys.common.config.ConfigOres;
import com.oitsjustjose.geolosys.common.config.ModConfig;
import com.oitsjustjose.geolosys.common.world.OreGenerator;
import com.oitsjustjose.geolosys.common.world.StoneGenerator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.DimensionManager;

/**
 * The Geolosys API is intended for use by anyone who wants to tap into all the locations that deposits exist
 * Access is pretty easy, just reference this class's currentWorldDeposits HashMap
 */
public class GeolosysAPI
{
    // A K:V pair of block states to pair deposits with their samples
    public static HashMap<IBlockState, IBlockState> oreBlocks = new HashMap<>();
    // A collection of blocks which Geolosys can replace in generation
    public static ArrayList<IBlockState> replacementMats = new ArrayList<>();
    // A matched pair of IBlockStates whose K:V = block:replacement
    public static HashMap<IBlockState, List<IBlockState>> oreBlocksSpecific = new HashMap<>();
    // A collection of blocks to ignore in the OreConverter feature
    public static ArrayList<IBlockState> oreConverterBlacklist = new ArrayList<>();
    // A K:V pair of IBlockStates with their sample sizes
    public static HashMap<IBlockState, Integer> sampleCounts = new HashMap<>();
    // Some local instance variables I don't want others having access to..
    private static File depositFileLocation = null;
    private static File regenFileLocation = null;
    private static HashMap<ChunkPosSerializable, String> currentWorldDeposits = new HashMap<>();
    private static LinkedHashMap<ChunkPosSerializable, Boolean> regennedChunks = new LinkedHashMap<>();
    private static Executor ioExecutor = Executors.newSingleThreadExecutor();

    /**
     * @return The world's current deposits throughout the world. The string is formatted as modid:block:meta
     */
    @SuppressWarnings("unchecked")

    public static HashMap<ChunkPosSerializable, String> getCurrentWorldDeposits()
    {
        return (HashMap<ChunkPosSerializable, String>) currentWorldDeposits.clone();
    }

    /**
     * @param pos   The Mojang ChunkPos to act as a key
     * @param state The String to act as a value
     */
    public static void putWorldDeposit(ChunkPos pos, int dimension, String state)
    {
        currentWorldDeposits.put(new ChunkPosSerializable(pos, dimension), state);
        if (ModConfig.featureControl.debugGeneration)
        {
            int total = 0;
            for (ChunkPosSerializable chunk : currentWorldDeposits.keySet())
            {
                if (currentWorldDeposits.get(chunk).equals(state))
                {
                    total++;
                }
            }
            Geolosys.getInstance().LOGGER.info(state + ": " + total + "/" + currentWorldDeposits.keySet().size());
            Geolosys.getInstance().LOGGER.info(state + ": " + (100 * (total / (1f * currentWorldDeposits.keySet().size()))) + "%");
        }
    }

    /**
     * @param pos   The ChunkPosSerializable to act as a key
     * @param state The String to act as a value
     */
    public static void putWorldDeposit(ChunkPosSerializable pos, String state)
    {
        currentWorldDeposits.put(pos, state);
    }

    /**
     * Reads in the currentWorldDeposits from GeolosysDeposits.dat
     */
    @SuppressWarnings("unchecked")
    public static void readFromFile()
    {
        if (DimensionManager.getCurrentSaveRootDirectory() == null)
        {
            return;
        }
        if (depositFileLocation == null)
        {
            depositFileLocation = new File(DimensionManager.getCurrentSaveRootDirectory() + File.separator + "GeolosysDeposits.dat");
        }
        if (regenFileLocation == null)
        {
            regenFileLocation = new File(DimensionManager.getCurrentSaveRootDirectory() + File.separator + "GeolosysRegen.dat");
        }
        try
        {
            if (depositFileLocation.exists())
            {
                FileInputStream fileInDeposits = new FileInputStream(depositFileLocation);
                ObjectInputStream inDeposits = new ObjectInputStream(fileInDeposits);
                currentWorldDeposits = (HashMap<ChunkPosSerializable, String>) inDeposits.readObject();
                inDeposits.close();
                fileInDeposits.close();

                FileInputStream fileInRegen = new FileInputStream(regenFileLocation);
                ObjectInputStream inRegen = new ObjectInputStream(fileInRegen);
                regennedChunks = (LinkedHashMap<ChunkPosSerializable, Boolean>) inRegen.readObject();
                inRegen.close();
                fileInRegen.close();
            }
        }
        catch (IOException i)
        {
            Geolosys.getInstance().LOGGER.error("There was an error loading GeolosysDeposits.dat");
        }
        catch (ClassNotFoundException c)
        {
            Geolosys.getInstance().LOGGER.error("There was an error in the code for deserialization. Please contact oitsjustjose on GitHub with a log");
            Geolosys.getInstance().LOGGER.error(c.getMessage());
        }
    }

    /**
     * Writes the currentWorldDeposits to GeolosysDeposits.dat
     */
    public static void writeToFile()
    {
        if (DimensionManager.getCurrentSaveRootDirectory() == null)
        {
            return;
        }
        if (depositFileLocation == null)
        {
            depositFileLocation = new File(DimensionManager.getCurrentSaveRootDirectory() + File.separator + "GeolosysDeposits.dat");
        }
        if (regenFileLocation == null)
        {
            regenFileLocation = new File(DimensionManager.getCurrentSaveRootDirectory() + File.separator + "GeolosysRegen.dat");
        }

        final HashMap<ChunkPosSerializable, String> currentWorldDepositsCopy = GeolosysAPI.getCurrentWorldDeposits();
        final HashMap<ChunkPosSerializable, Boolean> regennedChunksCopy = GeolosysAPI.getRegennedChunks();

        ioExecutor.execute(() -> {
            try
            {
                FileOutputStream fileOutDeposits = new FileOutputStream(depositFileLocation);
                ObjectOutputStream outDeposits = new ObjectOutputStream(fileOutDeposits);
                outDeposits.writeObject(currentWorldDepositsCopy);
                outDeposits.close();
                fileOutDeposits.close();

                FileOutputStream fileOutRegen = new FileOutputStream(regenFileLocation);
                ObjectOutputStream outRegen = new ObjectOutputStream(fileOutRegen);
                outRegen.writeObject(regennedChunksCopy);
                outRegen.close();
                fileOutRegen.close();
            }
            catch (IOException i)
            {
                i.printStackTrace();
            }
        });
    }

    /**
     * Adds a deposit for Geolosys to handle the generation of.
     *
     * @param oreBlock     The block you want UNDERGROUND as an ore
     * @param sampleBlock  The block you want ON THE SURFACE as a sample
     * @param yMin         The minimum Y level this deposit can generate at
     * @param yMax         The maximum Y level this deposit can generate at
     * @param size         The size of the deposit
     * @param chance       The chance of the deposit generating (higher = more likely)
     * @param dimBlacklist An array of dimension numbers in which this deposit cannot generate
     */
    public static void registerMineralDeposit(IBlockState oreBlock, IBlockState sampleBlock, int yMin, int yMax, int size, int chance, int[] dimBlacklist)
    {
        OreGenerator.addOreGen(oreBlock, size, yMin, yMax, chance, dimBlacklist);
        oreBlocks.put(oreBlock, sampleBlock);
        sampleCounts.put(sampleBlock, size);
    }

    /**
     * Adds a deposit for Geolosys to handle the generation of.
     *
     * @param oreBlock    The block you want UNDERGROUND as an ore
     * @param sampleBlock The block you want ON THE SURFACE as a sample
     * @param ore         The ore (wrapper object) containing the generation info
     */
    public static void registerMineralDeposit(IBlockState oreBlock, IBlockState sampleBlock, ConfigOres.Ore ore)
    {
        registerMineralDeposit(oreBlock, sampleBlock, ore.getMinY(), ore.getMaxY(), ore.getSize(), ore.getChance(), ore.getBlacklist());
    }

    /**
     * Adds a deposit for Geolosys to handle the generation of.
     *
     * @param oreBlock           The block you want UNDERGROUND as an ore
     * @param sampleBlock        The block you want ON THE SURFACE as a sample
     * @param yMin               The minimum Y level this deposit can generate at
     * @param yMax               The maximum Y level this deposit can generate at
     * @param size               The size of the deposit
     * @param chance             The chance of the deposit generating (higher = more likely)
     * @param dimBlacklist       An array of dimension numbers in which this deposit cannot generate
     * @param blockStateMatchers A collection of blocks that this entry specifically can replace
     */
    public static void registerMineralDeposit(IBlockState oreBlock, IBlockState sampleBlock, int yMin, int yMax, int size, int chance, int[] dimBlacklist, List<IBlockState> blockStateMatchers)
    {
        oreBlocks.put(oreBlock, sampleBlock);
        sampleCounts.put(sampleBlock, size);
        oreBlocksSpecific.put(oreBlock, blockStateMatchers);
        OreGenerator.addOreGen(oreBlock, size, yMin, yMax, chance, dimBlacklist);
    }


    /**
     * Ads a stone type for Geolosys to handle the generation of.
     *
     * @param stoneBlock The block you want to generate
     * @param yMin       The minimum Y level this deposit can generate at
     * @param yMax       The maximum Y level this deposit can generate at
     * @param chance     The chance of the deposit generating (higher = more likely)
     */
    public static void registerStoneDeposit(IBlockState stoneBlock, int yMin, int yMax, int chance)
    {
        StoneGenerator.addStoneGen(stoneBlock, yMin, yMax, chance);
        replacementMats.add(stoneBlock);
    }

    /**
     * Marks a chunk as having been regenerated
     * - this is for the "Retroactively replace existing ores in world" option
     *
     * @param pos The ChunkPos to add to
     */
    public static void markChunkRegenned(ChunkPos pos, int dimension)
    {
        markChunkRegenned(new ChunkPosSerializable(pos, dimension));
    }

    /**
     * Marks a chunk as having been regenerated
     * - this is for the "Retroactively replace existing ores in world" option
     *
     * @param pos The ChunkPosSerializeable to add to
     */
    public static void markChunkRegenned(ChunkPosSerializable pos)
    {
        regennedChunks.put(pos, true);
    }

    /**
     * Checks if a chunk has been retroactively replaced with Geolosys ores
     *
     * @param pos The ChunkPos to check
     * @return True if the chunk is in the map and has been marked as regenned
     */
    public static boolean hasChunkRegenned(ChunkPos pos, int dimension)
    {
        return hasChunkRegenned(new ChunkPosSerializable(pos, dimension));
    }

    /**
     * Checks if a chunk has been retroactively replaced with Geolosys ores
     *
     * @param pos The ChunkPos to check
     * @return True if the chunk is in the map and has been marked as regenned
     */
    public static boolean hasChunkRegenned(ChunkPosSerializable pos)
    {
        for (ChunkPosSerializable c : regennedChunks.keySet())
        {
            if (c.getX() == pos.getX() && c.getZ() == pos.getZ() && c.getDimension() == pos.getDimension())
            {
                return regennedChunks.get(c);
            }
        }
        return false;
    }

    /**
     * @return The world's current deposits throughout the world. The string is formatted as modid:block:meta
     */
    @SuppressWarnings("unchecked")

    public static HashMap<ChunkPosSerializable, Boolean> getRegennedChunks()
    {
        return (HashMap<ChunkPosSerializable, Boolean>) regennedChunks.clone();
    }


    /**
     * ChunkPosSerializable is a serializable version of Mojang's ChunkPos
     * As such, it stores a chunk's X and Z position
     */
    public static class ChunkPosSerializable implements Serializable
    {
        private int x;
        private int z;
        private int dim;

        /**
         * @param pos A Mojang ChunkPos initializer for ChunkPosSerializable
         */
        public ChunkPosSerializable(ChunkPos pos, int dim)
        {
            this(pos.x, pos.z, dim);
        }

        /**
         * @param x The X position which the Chunk starts at
         * @param z The Z position which the Chunk starts at
         */
        public ChunkPosSerializable(int x, int z, int dim)
        {
            this.x = x;
            this.z = z;
            this.dim = dim;
        }

        /**
         * @return The X value at which the Chunk starts at
         */
        public int getX()
        {
            return this.x;
        }

        /**
         * @return The Z value at which the Chunk starts at
         */
        public int getZ()
        {
            return this.z;
        }

        /**
         * @return The dimension of the chunk
         */
        public int getDimension()
        {
            return this.dim;
        }

        /**
         * @return A Mojang ChunkPos variant of this object
         */
        public ChunkPos toChunkPos()
        {
            return new ChunkPos(this.x, this.z);
        }

        @Override
        public String toString()
        {
            return "[" + this.getX() + "," + this.getZ() + "," + this.getDimension() + "]";
        }

        @Override
        public boolean equals(Object other)
        {
            if (other == this)
            {
                return true;
            }
            else if (other instanceof ChunkPosSerializable)
            {
                ChunkPosSerializable c = (ChunkPosSerializable) other;
                return c.getX() == this.getX() && c.getZ() == this.getZ() && c.getDimension() == this.getDimension();
            }
            return false;
        }
    }
}
