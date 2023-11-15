package com.enginemachiner.honkytones;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface LidAnimatorBehaviour { void renderStep(); }
