package android.content.res;

import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import se.aqba.framework.elpollo.helpers.ClassHelper;

public final class PTypedArray extends TypedArray {
    private static final String TAG = "ElPollo.TypedArray";

    private PTypedArray() {
        super(null, null, null, 0);
    }

    @Override
    public int getResourceId(int index, int defValue) {
        if(hasReplacement(index)) {
            return defValue;
        }

        return super.getResourceId(index, defValue);
    }

    @Override
    public boolean getBoolean(int index, boolean defValue) {
        Boolean replacement = getReplacement(index, PResources.ResType.ResTypeBoolean);
        if(replacement != null) {
            return replacement;
        }

        return super.getBoolean(index, defValue);
    }

    @Override
    public int getColor(int index, int defValue) {
        Integer replacement = getReplacement(index, PResources.ResType.ResTypeColor);
        if(replacement != null) {
            return replacement;
        }

        return super.getColor(index, defValue);
    }

    @Override
    public ColorStateList getColorStateList(int index) {
        ColorStateList replacement = getReplacement(index, PResources.ResType.ResTypeColorStateList);
        if(replacement != null) {
            return replacement;
        }

        return super.getColorStateList(index);
    }

    @Override
    public float getDimension(int index, float defValue) {
        Float replacement = getReplacement(index, PResources.ResType.ResTypeDimension);
        if(replacement != null) {
            return replacement;
        }

        return super.getDimension(index, defValue);
    }

    @Override
    public int getDimensionPixelOffset(int index, int defValue) {
        Integer replacement = getReplacement(index, PResources.ResType.ResTypeDimensionPixelOffset);
        if(replacement != null) {
            return replacement;
        }

        return super.getDimensionPixelOffset(index, defValue);
    }

    @Override
    public int getDimensionPixelSize(int index, int defValue) {
        Integer replacement = getReplacement(index, PResources.ResType.ResTypeDimensionPixelSize);
        if(replacement != null) {
            return replacement;
        }

        return super.getDimensionPixelSize(index, defValue);
    }

    @Override
    public Drawable getDrawable(int index) {
        Object replacement = getReplacement(index, PResources.ResType.ResTypeDrawable);
        if(replacement != null) {
            if(replacement instanceof Integer) {
                return new ColorDrawable((Integer)replacement);
            }
            return (Drawable)replacement;
        }

        return super.getDrawable(index);
    }

    @Override
    public float getFloat(int index, float defValue) {
        Float replacement = getReplacement(index, PResources.ResType.ResTypeFloat);
        if(replacement != null) {
            return replacement;
        }

        return super.getFloat(index, defValue);
    }

    @Override
    public float getFraction(int index, int base, int pbase, float defValue) {
        Float replacement = getReplacement(index, PResources.ResType.ResTypeFraction, base, pbase);
        if(replacement != null) {
            return replacement;
        }

        return super.getFraction(index, base, pbase, defValue);
    }

    @Override
    public int getInt(int index, int defValue) {
        return getInteger(index, defValue);
    }

    @Override
    public int getInteger(int index, int defValue) {
        Integer replacement = getReplacement(index, PResources.ResType.ResTypeInteger);
        if(replacement != null) {
            return replacement;
        }

        return super.getInt(index, defValue);
    }

    @Override
    public int getLayoutDimension(int index, String name) {
        Integer replacement = getReplacement(index, PResources.ResType.ResTypeLayoutDimension);
        if(replacement != null) {
            return replacement;
        }

        return super.getLayoutDimension(index, name);
    }

    @Override
    public int getLayoutDimension(int index, int defValue) {
        Integer replacement = getReplacement(index, PResources.ResType.ResTypeLayoutDimension);
        if(replacement != null) {
            return replacement;
        }

        return super.getLayoutDimension(index, defValue);
    }

    @Override
    public String getString(int index) {
        String replacement = getReplacement(index, PResources.ResType.ResTypeString);
        if(replacement != null) {
            return replacement;
        }

        return super.getString(index);
    }

    @Override
    public CharSequence getText(int index) {
        CharSequence replacement = getReplacement(index, PResources.ResType.ResTypeText);
        if(replacement != null) {
            return replacement;
        }

        return super.getText(index);
    }

    @Override
    public CharSequence[] getTextArray(int index) {
    CharSequence[] replacement = getReplacement(index, PResources.ResType.ResTypeTextArray);
        if(replacement != null) {
            return replacement;
        }

        return super.getTextArray(index);
    }

    public static PTypedArray fromTypedArray(TypedArray from) {
        ClassHelper.setObjectClass(from, PTypedArray.class);

        return (PTypedArray)from;
    }

    private boolean hasReplacement(int index) {
        if(!(super.getResources() instanceof PResources)) {
            return false;
        }

        PResources res = (PResources)super.getResources();

        int id = super.getResourceId(index, 0);
        if(id == 0) {
            return false;
        }

        return res.hasReplacement(id);
    }

    private <T> T getReplacement(int index, PResources.ResType type, Object... args) {
        if(!(super.getResources() instanceof PResources)) {
            return null;
        }

        PResources res = (PResources)super.getResources();

        int id = super.getResourceId(index, 0);
        if(id == 0) {
            return null;
        }

        return res.getReplacement(id, type, args);
    }
} 
