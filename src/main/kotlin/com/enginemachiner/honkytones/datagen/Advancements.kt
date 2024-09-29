package com.enginemachiner.honkytones.datagen

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlock
import com.enginemachiner.honkytones.items.instruments.*
import com.enginemachiner.honkytones.items.music_player.RadioItem
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider
import net.minecraft.advancement.AdvancementCriterion
import net.minecraft.advancement.AdvancementEntry
import net.minecraft.advancement.AdvancementFrame
import net.minecraft.advancement.criterion.*
import net.minecraft.entity.EntityType
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.predicate.NumberRange
import net.minecraft.predicate.entity.EntityEquipmentPredicate
import net.minecraft.predicate.entity.EntityPredicate
import net.minecraft.predicate.item.EnchantmentPredicate
import net.minecraft.predicate.item.ItemPredicate
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.*
import java.util.function.Consumer
import kotlin.reflect.KClass

// TODO: Parrots vibing and fuel advancement.

class Advancements( output: FabricDataOutput ) : FabricAdvancementProvider(output) {

    override fun generateAdvancement( consumer: Consumer<AdvancementEntry> ) {

        Root.build( consumer, "any_instrument" )

        Doot.build(consumer);           Keyboard.build(consumer)

        addInstruments( percussion, consumer )
        addInstruments( strings, consumer )
        addInstruments( winds, consumer )
        addInstruments( keys, consumer )

        Enchantment.build(consumer);        FloppyDisk.build(consumer)

        DigitalConsole.build(consumer);         MusicalStorage.build(consumer)

        MusicPlayer.build(consumer);            Radio.build(consumer)

        Remote.build(consumer)

    }

    private companion object {

        val tag = Tags.instruments

        val percussion = listOf( DrumSet::class, SFX::class )

        val strings = listOf( ElectricGuitar::class, Violin::class )

        val winds = listOf( Trombone::class, Oboe::class )

        val keys = listOf(
            Accordion::class, Organ::class, Harpsichord::class,
            ElectricPiano::class, Rhodes::class
        )

        fun addInstruments( list: List< KClass<*> >, consumer: Consumer<AdvancementEntry> ) {

            var parent: ModAdvancement = Keyboard

            list.forEach {

                parent = Instrument( it, parent );          parent.build(consumer)

            }

        }

        interface RecipeCriterion : ModID {

            fun genericConditions(): AdvancementCriterion<*> {

                return RecipeUnlockedCriterion.create( classID() )

            }

        }

        object Root : ModAdvancement() {

            override val background = Identifier("minecraft:textures/block/quartz_block_top.png")

            override fun title(): Text { return Text.of(MOD_TITLE) }

            override val icon = ModItemGroup.item

            override fun conditions(): AdvancementCriterion<*> {

                val predicate = ItemPredicate.Builder.create().tag(tag).build()

                return InventoryChangedCriterion.Conditions.items(predicate)

            }

        }

        object Doot : ModAdvancement() {

            override val parent = Keyboard

            override fun title(): Text { return Text.of("DOOT!") }

            override val icon = modItem( Trumpet::class )

            override val toast = true;          override val announce = true

            override val frame = AdvancementFrame.CHALLENGE

            override fun conditions(): AdvancementCriterion<*> {

                val predicate = ItemPredicate.Builder.create().items(icon)
                val equipment = EntityEquipmentPredicate.Builder.create().mainhand(predicate).build()
                val entity = EntityPredicate.Builder.create().equipment(equipment).type( EntityType.SKELETON )

                return OnKilledCriterion.Conditions.createPlayerKilledEntity(entity)

            }

        }

        object Keyboard : ModAdvancement(), RecipeCriterion {

            override val parent = Root

            override val icon = modItem( Keyboard::class )

            override fun conditions(): AdvancementCriterion<*> { return genericConditions() }

        }

        class Instrument( kClass: KClass<*>, override val parent: ModAdvancement ) : ModAdvancement() {

            override val icon = modItem(kClass)


            private val modID = icon as ModID

            override var name = modID.className();      init { key = "instrument" }


            private val title = "item.$MOD_NAME.$name"

            override fun title(): Text { return Text.translatable(title) }


            override fun conditions(): AdvancementCriterion<*> {

                return InventoryChangedCriterion.Conditions.items(icon)

            }

        }

        object Enchantment : ModAdvancement() {

            override fun className(): String { return "ranged_enchantment" }

            override val parent = Keyboard

            val enchantment = RangedEnchantment.registered

            init { key = "enchantment" }

            override val icon: Item = Items.ENCHANTED_BOOK

            override val toast = true;          override val announce = true

            override fun conditions(): AdvancementCriterion<*> {

                val range = NumberRange.IntRange.ANY
                val enchantmentPredicate = EnchantmentPredicate( enchantment, range )

                val predicate = ItemPredicate.Builder.create()
                    .enchantment( enchantmentPredicate ).tag(tag).build()

                return InventoryChangedCriterion.Conditions.items(predicate)

            }

        }

        object FloppyDisk : ModAdvancement(), RecipeCriterion {

            override val parent = Keyboard

            override val icon = modItem( FloppyDisk::class )

            override fun conditions(): AdvancementCriterion<*> { return genericConditions() }

        }

        object DigitalConsole : ModAdvancement(), RecipeCriterion {

            override val parent = FloppyDisk

            override val icon = modItem( DigitalConsole::class )

            override fun conditions(): AdvancementCriterion<*> { return genericConditions() }

        }

        object MusicalStorage : ModAdvancement(), RecipeCriterion {

            override val parent = DigitalConsole

            override val icon = modItem( MusicalStorage::class )

            override fun conditions(): AdvancementCriterion<*> { return genericConditions() }

        }

        object MusicPlayer : ModAdvancement(), RecipeCriterion {

            override val parent = MusicalStorage

            override val icon = modItem( MusicPlayerBlock::class )

            override val frame = AdvancementFrame.GOAL

            override val toast = true;          override val announce = true

            override fun conditions(): AdvancementCriterion<*> { return genericConditions() }

        }

        object Radio : ModAdvancement(), RecipeCriterion {

            override val parent = MusicPlayer

            override val icon = RadioItem.registeredItem

            override fun conditions(): AdvancementCriterion<*> { return genericConditions() }

        }

        object Remote : ModAdvancement(), RecipeCriterion {

            override val parent = MusicPlayer

            override val icon = modItem( Remote::class )

            override fun conditions(): AdvancementCriterion<*> { return genericConditions() }

        }

    }

}