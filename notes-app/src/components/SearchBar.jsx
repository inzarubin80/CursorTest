import './SearchBar.css';

/**
 * @param {{ value: string, onChange: (value: string) => void, placeholder?: string }}
 */
export default function SearchBar({ value, onChange, placeholder = 'Поиск по заметкам…' }) {
  return (
    <div className="search-bar">
      <input
        type="search"
        className="search-bar-input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label="Поиск"
      />
    </div>
  );
}
