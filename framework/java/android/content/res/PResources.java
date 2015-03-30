package android.content.res;

import android.content.pm.PackageParser;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import se.aqba.framework.elpollo.ElPollo;

public class PResources extends Resources {
    private static final String TAG = "ElPollo.PResources";

    private final static HashMap<String, HashMap<Integer, Object>> sReplacements = new HashMap<String, HashMap<Integer, Object>>();

    private final HashMap<Integer, XmlResourceParser> mCachedLayouts = new HashMap<Integer, XmlResourceParser>();

    private String mResDir;
    private String mPackage;

    public PResources() {
        super(AssetManager.getSystem(), new DisplayMetrics(), null);
        super.updateConfiguration(null, null);

        mResDir = null;
        mPackage = null;
    }

    public PResources(Resources res, IBinder token, String resDir) {
        super(res.getAssets(), res.getDisplayMetrics(), res.getConfiguration(), res.getCompatibilityInfo(), token);

        mResDir = resDir;
        try {
            mPackage = PackageParser.parsePackageLite(new File(resDir), 0).packageName;
        } catch(Throwable e) {
            mPackage = null;
        }
    }

    @Override
    public boolean getBoolean(int id)  throws NotFoundException {
        Boolean replacement = getReplacement(id, ResType.ResTypeBoolean);
        if(replacement != null) {
            return replacement.booleanValue();
        }

        return super.getBoolean(id);
    }

    @Override
    public int getColor(int id) throws NotFoundException {
        Integer replacement = getReplacement(id, ResType.ResTypeColor);
        if(replacement != null) {
            return replacement.intValue();
        }

        return super.getColor(id);
    }

    @Override
    public ColorStateList getColorStateList(int id) throws NotFoundException {
        ColorStateList replacement = getReplacement(id, ResType.ResTypeColorStateList);
        if(replacement != null) {
            return replacement;
        }

        return super.getColorStateList(id);
    }

    @Override
    public float getDimension(int id) throws NotFoundException {
        Float replacement = getReplacement(id, ResType.ResTypeDimension);
        if(replacement != null) {
            return replacement.floatValue();
        }

        return super.getDimension(id);
    }

    @Override
    public int getDimensionPixelOffset(int id) throws NotFoundException {
        Integer replacement = getReplacement(id, ResType.ResTypeDimensionPixelOffset);
        if(replacement != null) {
            return replacement.intValue();
        }

        return super.getDimensionPixelOffset(id);
    }

    @Override
    public int getDimensionPixelSize(int id) throws NotFoundException {
        Integer replacement = getReplacement(id, ResType.ResTypeDimensionPixelSize);
        if(replacement != null) {
            return replacement.intValue();
        }

        return super.getDimensionPixelSize(id);
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        Object replacement = getReplacement(id, ResType.ResTypeDrawable);
        if(replacement != null) {
            if(replacement instanceof Integer) {
                return new ColorDrawable((Integer)replacement);
            }
            return (Drawable)replacement;
        }

        return super.getDrawable(id);
    }

    @Override
    public Drawable getDrawable(int id, Resources.Theme theme) throws NotFoundException {
        Object replacement = getReplacement(id, ResType.ResTypeDrawableTheme, theme);
        if(replacement != null) {
            if(replacement instanceof Integer) {
                return new ColorDrawable((Integer)replacement);
            }
            return (Drawable)replacement;
        }

        return super.getDrawable(id, theme);
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        Object replacement = getReplacement(id, ResType.ResTypeDrawableDensity, density);
        if(replacement != null) {
            if(replacement instanceof Integer) {
                return new ColorDrawable((Integer)replacement);
            }
            return (Drawable)replacement;
        }

        return super.getDrawableForDensity(id, density);
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density, Resources.Theme theme) throws NotFoundException {
        Object replacement = getReplacement(id, ResType.ResTypeDrawableDensityTheme, density, theme);
        if(replacement != null) {
            if(replacement instanceof Integer) {
                return new ColorDrawable((Integer)replacement);
            }
            return (Drawable)replacement;
        }

        return super.getDrawableForDensity(id, density, theme);
    }

    @Override
    public float getFraction(int id, int base, int pbase) throws NotFoundException {
        Float replacement = getReplacement(id, ResType.ResTypeFraction, base, pbase);
        if(replacement != null) {
            return replacement;
        }

        return super.getFraction(id, base, pbase);
    }

    @Override
    public int[] getIntArray(int id) {
        Object replacement = getReplacement(id, ResType.ResTypeIntegerArray);
        if(replacement != null) {
            return (int[])replacement;
        }

        return super.getIntArray(id);
    }

    @Override
    public int getInteger(int id) {
        Object replacement = getReplacement(id, ResType.ResTypeInteger);
        if(replacement != null) {
            return (int)replacement;
        }

        return super.getInteger(id);
    }

    @Override
    public XmlResourceParser getLayout(int id) {
        if(!mCachedLayouts.containsKey(id)) {
            mCachedLayouts.put(id, super.getLayout(id));
        }

        Object replacement = getReplacement(id, ResType.ResTypeLayout);
        if(replacement != null) {
            return (XmlResourceParser)replacement;
        }

        return super.getLayout(id);
    }

    @Override
    public String getQuantityString(int id, int quantity, Object... formatArgs) {
        Object replacement = getReplacement(id, ResType.ResTypeQuantityString, quantity, formatArgs);
        if(replacement != null) {
            return (String)replacement;
        }

        return super.getQuantityString(id, quantity, formatArgs);
    }

    @Override
    public String getQuantityString(int id, int quantity) {
        Object replacement = getReplacement(id, ResType.ResTypeQuantityString, quantity);
        if(replacement != null) {
            return (String)replacement;
        }

        return super.getQuantityString(id, quantity);
    }

    @Override
    public CharSequence getQuantityText(int id, int quantity) {
        Object replacement = getReplacement(id, ResType.ResTypeQuantityText, quantity);
        if(replacement != null) {
            return (CharSequence)replacement;
        }

        return super.getQuantityText(id, quantity);
    }

    @Override
    public String getString(int id) throws NotFoundException {
        String replacement = getReplacement(id, ResType.ResTypeString);
        if(replacement != null) {
            return replacement;
        }

        return super.getString(id);
    }

    @Override
    public String getString(int id, Object... formatArgs) throws NotFoundException {
        String replacement = getReplacement(id, ResType.ResTypeString, formatArgs);
        if(replacement != null) {
            return String.format(super.getConfiguration().locale, replacement, formatArgs);
        }

        return super.getString(id, formatArgs);
    }

    @Override
    public String[] getStringArray(int id) {
        Object replacement = getReplacement(id, ResType.ResTypeStringArray);
        if(replacement != null) {
            return (String[])replacement;
        }

        return super.getStringArray(id);
    }

    @Override
    public CharSequence getText(int id, CharSequence def) throws NotFoundException {
        CharSequence replacement = getReplacement(id, ResType.ResTypeText, def);
        if(replacement != null) {
            return replacement;
        }

        return super.getText(id, def);
    }

    @Override
    public CharSequence getText(int id) throws NotFoundException {
        CharSequence replacement = getReplacement(id, ResType.ResTypeText);
        if(replacement != null) {
            return replacement;
        }

        return super.getText(id);
    }

    @Override
    public CharSequence[] getTextArray(int id) {
        Object replacement = getReplacement(id, ResType.ResTypeTextArray);
        if(replacement != null) {
            return (CharSequence[])replacement;
        }

        return super.getTextArray(id);
    }

    /** 
     * Add a new resource replacement.<br/>
     * Internal use only.
     *
     * @param pkg The package whose resource we want to replace, null for system.
     * @param id The id of the resource.
     * @param replacement The replacement object, its type depends on<br/>
     *      the resource being replaced. Can also be a forwarder.
     * @see se.aqba.framework.elpollo.ElPollo.Resources
     * @see se.aqba.framework.elpollo.ElPollo.Resources.Forwarder
     */
    public static void addReplacement(String pkg, int id, Object replacement) {
        synchronized(sReplacements) {
            HashMap<Integer, Object> pkgRep = sReplacements.get(pkg);
            if(pkgRep == null) {
                pkgRep = new HashMap<Integer, Object>();
                sReplacements.put(pkg, pkgRep);
            }

            Log.d(TAG, "addReplacement " + pkg + ":" + id);
            pkgRep.put(id, replacement);
        }
    }

    public static PResources getSystem() {
        return (PResources)Resources.getSystem();
    }

    /**
     * Get the layout resource id from an Xml parser.<br/>
     * Internal use only.
     * @param parser The Xml parser.
     * @return The layout resource id.
     */
    public int getLayoutId(XmlResourceParser parser) {
        for(Map.Entry<Integer, XmlResourceParser> entry: mCachedLayouts.entrySet()) {
            if(entry.getValue().equals(parser)) {
                return entry.getKey();
            }
        }

        return 0;
    }

    /**
     * Get the resource object's package.<br/>
     * Internal use only.
     * @return The package name.
     */
    public String getPackage() {
        return mPackage;
    }

    /**
     * Check if a replacement exists.<br/>
     * Internal use only.
     * @param id The id to check.
     * @return Whether a replacement with the specified id exists.
     */
    public boolean hasReplacement(int id) {
        return hasReplacement(id, false);
    }

    /**
     * Check if a replacement exists.<br/>
     * Internal use only.
     * @param id The id to check.
     * @param isInflater Whether this is called from a layout inflater.
     * @return Whether a replacement with the specified id exists.
     */
    public boolean hasReplacement(int id, boolean isInflater) {
        synchronized(sReplacements) {
            HashMap<Integer, Object> pkgRep = sReplacements.get(mPackage);
            if(pkgRep == null) {
                pkgRep = sReplacements.get("android");
            }
            if(pkgRep != null) {
                Object obj = pkgRep.get(id);
                if(!isInflater) {
                    return obj != null && !(obj instanceof ElPollo.Resources.ViewForwarder);
                }
                return obj != null;
            }
        }

        return false;
    }

    /**
     * Get a replaced object.<br/>
     * Internal use only.
     * @param id The resource id of the replacement.
     * @param type The type of resource.
     * @param args Variable list of arguments passed to a forwarder.
     * @return The replacement object.
     * @see ResType
     * @see se.aqba.framework.elpollo.ElPollo.Resources.Forwarder
     */
    @SuppressWarnings("unchecked")
    public <T> T getReplacement(int id, ResType type, Object... args) {
        synchronized(sReplacements) {
            HashMap<Integer, Object> pkgRep = sReplacements.get(mPackage);
            if(pkgRep == null) {
                pkgRep = sReplacements.get("android");
            }

            if(pkgRep != null) {
                try {
                    Object obj = pkgRep.get(id);
                    if(obj != null) {
                        if(obj instanceof ElPollo.Resources.Forwarder) {
                            ElPollo.Resources.Forwarder forwarder = (ElPollo.Resources.Forwarder)obj;
                            forwarder.setType(type);

                            if(obj instanceof ElPollo.Resources.DrawableForwarder) {
                                ElPollo.Resources.DrawableForwarder drawableForwarder = (ElPollo.Resources.DrawableForwarder)forwarder;
                                switch(type) {
                                    case ResTypeDrawable:
                                        return (T)drawableForwarder.get(id, TypedValue.DENSITY_NONE, null);
                                    case ResTypeDrawableTheme:
                                        return (T)drawableForwarder.get(id, TypedValue.DENSITY_NONE, (Resources.Theme)args[0]);
                                    case ResTypeDrawableDensity:
                                        return (T)drawableForwarder.get(id, (Integer)args[0], null);
                                    case ResTypeDrawableDensityTheme:
                                        return (T)drawableForwarder.get(id, (Integer)args[0], (Resources.Theme)args[1]);
                                    default:
                                        return null;
                                }
                            } else if(obj instanceof ElPollo.Resources.ViewForwarder) {
                                // A view forwarder is only applicable on a layout inflater.
                                if(type != ResType.ResTypeLayoutInflater) {
                                    return null;
                                }

                                ElPollo.Resources.ViewForwarder viewForwarder = (ElPollo.Resources.ViewForwarder)forwarder;
                                T ret = (T)viewForwarder.get(id, (View)args[0]);
                                if(ret == null) {
                                    ret = (T)args[0];
                                }

                                return ret;
                            }

                            return (T)forwarder.get();
                        }
                    }

                    return (T)obj;
                } catch(ClassCastException e) {
                    Log.e(TAG, "getReplacement " + id, e);
                }
            }
        }

        return null;
    }

    public static enum ResType {
        ResTypeUninit,
        ResTypeBoolean,
        ResTypeColor,
        ResTypeColorStateList,
        ResTypeDimension,
        ResTypeDimensionPixelOffset,
        ResTypeDimensionPixelSize,
        ResTypeDrawable,
        ResTypeDrawableTheme,
        ResTypeDrawableDensity,
        ResTypeDrawableDensityTheme,
        ResTypeFloat,
        ResTypeFraction,
        ResTypeIntegerArray,
        ResTypeInteger,
        ResTypeLayout,
        ResTypeLayoutInflater,
        ResTypeLayoutDimension,
        ResTypeLayoutDimensionName,
        ResTypeQuantityString,
        ResTypeQuantityText,
        ResTypeString,
        ResTypeStringArray,
        ResTypeText,
        ResTypeTextArray,
    }
}