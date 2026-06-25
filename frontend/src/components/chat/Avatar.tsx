const getInitials = (name: string) => {
  if (!name) return '??';
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.substring(0, 2).toUpperCase();
};

interface AvatarProps {
  url?: string | null;
  name: string;
  size?: number | string; // size in px or string class, we can use className to override size anyway
  className?: string; // extra classes like w-10 h-10
  title?: string; // tooltips
}

export function Avatar({ url, name, size, className = '', title }: AvatarProps) {
  const style = size ? { width: size, height: size } : undefined;
  
  if (url) {
    return (
      <img 
        src={url} 
        alt={name} 
        title={title || name}
        style={style} 
        className={`rounded-full object-cover flex-shrink-0 ${className}`} 
      />
    );
  }

  return (
    <div 
      style={style} 
      className={`rounded-full flex items-center justify-center text-white font-medium flex-shrink-0 bg-gradient-to-b from-[#87baff] to-[#0068ff] ${className}`}
      title={title || name}
    >
      {getInitials(name)}
    </div>
  );
}
